"""Offline deployment checks; no Docker, SSH, existing database or printer access."""

import base64
import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import database
import dev_deploy
import model
import init_config


def fixture(mode="secure", environment="dev"):
    config = json.loads((Path(__file__).resolve().parents[1] / "instance.example.json").read_text())
    config.update(mode=mode, environment=environment, project=f"edol-{environment}-{mode}", revision="a" * 40)
    config["secrets"] = {k: hashlib.sha256(k.encode()).hexdigest() for k in config["secrets"]}
    for key in ("agent_pairing", "agent_credential", "terminal_pairing", "terminal_credential", "telegram"):
        config["secrets"][key] = base64.b64encode(hashlib.sha256(key.encode()).digest()).decode()
    if mode == "home":
        config["hub_origin"] = "http://192.168.0.200:18090"
        config["secrets"]["home"] = hashlib.sha256(b"home-db-test").hexdigest()
        config["secrets"] = {k: config["secrets"][k] for k in ("postgres", "home", "core_web_admin")}
    return config


class DeploymentTests(unittest.TestCase):
    def test_new_configs_validate_and_use_independent_fresh_keys(self):
        for mode in ("home", "secure"):
            first, second = init_config.new_config(mode), init_config.new_config(mode)
            model.validate(first)
            model.validate(second)
            self.assertNotEqual(first["secrets"], second["secrets"])
            self.assertNotIn("seed", first)
            self.assertEqual(first["environment"], "dev")

    def test_four_supported_combinations(self):
        for mode in ("home", "secure"):
            for environment in ("dev", "prod"):
                c = model.validate(fixture(mode, environment))
                result = model.compose(c, Path("/source"), Path("/instance/state"))
                self.assertEqual(result["name"], f"edol-{environment}-{mode}")
                self.assertTrue(set(model.SERVICES).issubset(result["services"]))
                self.assertTrue(all("healthcheck" in s for s in result["services"].values()))

    def test_provisioning_never_resets_or_inserts_application_data(self):
        for mode in ("secure", "home"):
            sql = database.provision(fixture(mode))
            for prohibited in ("DROP DATABASE", "DROP SCHEMA", "TRUNCATE", "INSERT INTO hub.", "INSERT INTO core.", "INSERT INTO ams."):
                self.assertNotIn(prohibited, sql)
        self.assertFalse(hasattr(database, "seed"))

    def test_dev_action_rejects_production_before_commands(self):
        with patch("dev_deploy.run") as command:
            with self.assertRaises(ValueError):
                dev_deploy.Deployment(fixture(environment="prod"), Path("/state")).apply()
            command.assert_not_called()

    def test_wrong_database_volume_is_rejected(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        info = [{"Config": {"Labels": {"com.docker.compose.project": "edol-dev-secure", "dev.edol.environment": "dev"}},
                 "Mounts": [{"Name": "unrelated-volume", "Destination": "/var/lib/postgresql/data"}]}]
        with patch.object(deployment, "dc", return_value="container"), patch("dev_deploy.run", return_value=json.dumps(info)):
            with self.assertRaises(ValueError):
                deployment.guard_database()

    def test_stop_selects_only_the_two_new_dev_projects(self):
        with patch("dev_deploy.run", return_value="") as command:
            dev_deploy.stop_dev_containers()
        self.assertEqual([call.args[0][-1] for call in command.call_args_list],
                         ["label=com.docker.compose.project=" + p for p in dev_deploy.DEV_PROJECTS])

    def test_identity_keys_are_stable_across_code_revisions(self):
        c = fixture()
        changed = copy.deepcopy(c)
        changed["revision"] = "b" * 40
        self.assertEqual(dev_deploy.persistent_fingerprint(c), dev_deploy.persistent_fingerprint(changed))
        changed["secrets"]["agent_credential"] = "changed"
        self.assertNotEqual(dev_deploy.persistent_fingerprint(c), dev_deploy.persistent_fingerprint(changed))

    def test_private_addresses_do_not_collide(self):
        result = model.compose(fixture(), Path("/source"), Path("/state"))
        addresses = [s["networks"]["private"]["ipv4_address"] for s in result["services"].values()]
        self.assertEqual(len(addresses), len(set(addresses)))

    def test_home_has_no_oidc_or_secure_roles(self):
        c = fixture("home")
        result = model.compose(c, Path("/source"), Path("/state"))
        self.assertNotIn("keycloak", result["services"])
        self.assertNotIn("KEYCLOAK", json.dumps(result))
        self.assertNotIn("reverse-proxy", result["networks"])
        self.assertNotIn("core_runtime", database.provision(c))

    def test_secure_core_trusted_clients_use_value_compatible_csv(self):
        for environment in ("dev", "prod"):
            result = model.compose(fixture(environment=environment), Path("/source"), Path("/state"))
            props = json.loads(result["services"]["edolcore"]["environment"]["SPRING_APPLICATION_JSON"])
            self.assertEqual(props["edol-core.security.trusted-service-client-ids"],
                             "edol-hub-service,edol-ams-service,edol-notify-service")

    def test_realm_has_no_application_user_in_any_environment(self):
        template = json.loads((dev_deploy.ROOT / "docker/keycloak/realm/edol-realm.json").read_text())
        for environment in ("dev", "prod"):
            result = model.realm(fixture(environment=environment), template)
            self.assertNotIn("users", result)
            self.assertTrue(result["registrationAllowed"])
            self.assertTrue(result["verifyEmail"])
            browser, *services = result["clients"]
            self.assertFalse(browser["serviceAccountsEnabled"])
            self.assertFalse(browser["directAccessGrantsEnabled"])
            self.assertEqual(browser["attributes"]["pkce.code.challenge.method"], "S256")
            self.assertEqual(len(services), 3)
            self.assertTrue(all(not s["standardFlowEnabled"] and not s["directAccessGrantsEnabled"] for s in services))

    def test_runtime_credentials_are_scoped(self):
        c = fixture()
        result = model.compose(c, Path("/source"), Path("/state"))
        for service in model.SERVICES:
            text = json.dumps(result["services"][service])
            self.assertNotIn(c["secrets"]["postgres"], text)
            self.assertNotIn("JAVA_TOOL_OPTIONS", text)
            if service != "edolhub":
                self.assertNotIn(c["secrets"]["web"], text)

    def test_healthchecks_use_http_and_label_notify_liveness(self):
        result = model.compose(fixture(), Path("/source"), Path("/state"))
        for service in ("edolhub", "edolcore", "edolams", "keycloak"):
            self.assertIn("HTTP/1.1", result["services"][service]["healthcheck"]["test"][-1])
        self.assertEqual(result["services"]["edolnotify"]["labels"]["dev.edol.healthcheck"], "process-liveness-only")
        self.assertNotIn("Password", json.dumps(result["services"]["mqtt"]["healthcheck"]))

    def test_remote_docker_endpoint_is_rejected(self):
        with patch.dict("dev_deploy.os.environ", {"DOCKER_HOST": "tcp://unrelated:2375"}), patch("dev_deploy.run") as command:
            with self.assertRaises(ValueError):
                dev_deploy.validate_local_docker()
            command.assert_not_called()

    def test_examples_cannot_be_deployed(self):
        c = json.loads((Path(__file__).resolve().parents[1] / "instance.example.json").read_text())
        with self.assertRaises(ValueError):
            model.validate(c)

    def test_sql_literal_is_escaped(self):
        self.assertEqual(database.literal("O'Brien"), "'O''Brien'")


if __name__ == "__main__":
    unittest.main()
