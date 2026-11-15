"""Production isolation and fail-closed migration ordering; no runtime access."""

from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import init_config
import model
from dev_deploy import BackupFailure, execute_locked, failure_summary, validate_action
from prod_deploy import ProductionDeployment, validate_generation
from test_deployment import fixture


class ProductionTests(unittest.TestCase):
    def test_prod_cli_rejects_missing_generation_and_provision_bypass(self):
        config = fixture(environment="prod")
        for action, generation in (("deploy-dev", None), ("deploy-prod", None), ("deploy-prod", 0), ("provision-db", None)):
            with self.assertRaises(ValueError):
                validate_action(SimpleNamespace(action=action, mode="secure", source_generation=generation), config)
        validate_action(SimpleNamespace(action="deploy-prod", mode="secure", source_generation=20), config)

    def test_missing_authority_is_rejected_before_prepare_can_regenerate_it(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            (state / "deployed.json").write_text('{}')
            with patch("dev_deploy.prepare") as prepare:
                with self.assertRaises(ValueError):
                    execute_locked(SimpleNamespace(action="render"), fixture(environment="prod"), state)
            prepare.assert_not_called()

    def test_generation_rejects_older_and_divergent_history_but_allows_retry(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            validate_generation(state, 20, "a" * 40)
            (state / "source-generation.json").write_text(json.dumps({"generation": 20, "revision": "a" * 40}))
            validate_generation(state, 20, "a" * 40)
            validate_generation(state, 21, "b" * 40)
            for generation, revision in ((19, "a" * 40), (20, "b" * 40)):
                with self.assertRaises(ValueError):
                    validate_generation(state, generation, revision)

    def test_backup_manifest_is_written_only_after_all_protected_artifacts(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "secure/state"
            state.mkdir(parents=True)
            deployment = ProductionDeployment(fixture(environment="prod"), state)
            captures = []

            def capture(path, command):
                captures.append(command)
                path.write_bytes(b"synthetic protected backup")

            with patch.object(deployment, "sql", return_value="edol_hub\nedol_devices\nedol_keycloak\n"), \
                    patch.object(deployment, "capture", side_effect=capture), \
                    patch("prod_deploy.write_private", side_effect=lambda path, content: path.write_text(content)):
                deployment.backup()
            destination = next((Path(directory) / "backups").iterdir())
            manifest = json.loads((destination / "complete.json").read_text())
            self.assertEqual(set(manifest["sha256"]), {"globals.sql", "edol_hub.dump", "edol_devices.dump",
                                                       "edol_keycloak.dump", "instance.tar.gz"})
            self.assertEqual(captures[-1], ["tar", "-czf", "-", "-C", str(state.parent.parent), "secure"])

    def test_backup_diagnostics_identify_artifact_and_exit_code_without_secrets(self):
        for name, artifact in (("globals.sql", "globals"), ("edol_hub.dump", "edol_hub"),
                               ("instance.tar.gz", "instance")):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                with patch("prod_deploy.subprocess.run", return_value=SimpleNamespace(
                        returncode=1, stderr=b"secret password and private path")):
                    with self.assertRaises(BackupFailure) as caught:
                        ProductionDeployment.capture(Path(directory) / name, ["private-command"])
                self.assertEqual(failure_summary(caught.exception),
                                 f"Deployment stopped: stage=backup artifact={artifact} exit_code=1")

    def test_backup_timeout_and_os_errors_are_safe(self):
        import subprocess
        for error in (OSError("secret path"), subprocess.TimeoutExpired("secret command", 600)):
            with tempfile.TemporaryDirectory() as directory:
                with patch("prod_deploy.subprocess.run", side_effect=error):
                    with self.assertRaises(BackupFailure) as caught:
                        ProductionDeployment.capture(Path(directory) / "instance.tar.gz", ["tar"])
                self.assertEqual(failure_summary(caught.exception),
                                 "Deployment stopped: stage=backup artifact=instance")

    def test_backup_command_failure_cannot_create_complete_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "secure/state"
            state.mkdir(parents=True)
            deployment = ProductionDeployment(fixture(environment="prod"), state)
            with patch.object(deployment, "capture", side_effect=RuntimeError("backup failed")):
                with self.assertRaises(RuntimeError):
                    deployment.backup()
            self.assertFalse(list((Path(directory) / "backups").rglob("complete.json")))

    def test_missing_existing_database_cannot_be_silently_reprovisioned(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "secure/state"
            state.mkdir(parents=True)
            (state / "deployed.json").write_text('{}')
            deployment = ProductionDeployment(fixture(environment="prod"), state)
            with patch.object(deployment, "capture"), patch.object(deployment, "sql", return_value="edol_devices\n"):
                with self.assertRaises(ValueError):
                    deployment.backup()
            self.assertFalse(list((Path(directory) / "backups").rglob("complete.json")))

    def test_generated_production_is_independent_of_dev(self):
        for mode in ("secure", "home"):
            config = init_config.new_config(mode, "prod")
            model.validate(config)
            dev = init_config.new_config(mode)
            self.assertNotEqual(config["subnet"], dev["subnet"])
            self.assertTrue(all(config[key] != dev[key] for key in ("hub_port", "core_port", "ams_port", "mqtt_port")))
            self.assertEqual(config["project"], "edol-prod-" + mode)
            self.assertNotEqual(config["secrets"], dev["secrets"])

    def test_production_rejects_dev_before_any_command(self):
        with patch("dev_deploy.run") as command:
            with self.assertRaises(ValueError):
                ProductionDeployment(fixture(), Path("/state")).apply()
            command.assert_not_called()

    def test_stop_preserves_postgres_and_selects_target_compose_only(self):
        deployment = ProductionDeployment(fixture(environment="prod"), Path("/state"))
        with patch.object(deployment, "dc") as command, patch("dev_deploy.stop_dev_containers") as dev_stop:
            deployment.stop_instances()
        dev_stop.assert_not_called()
        self.assertEqual(command.call_args.args, ("stop", "edolcore", "edolhub", "edolams", "edolnotify", "mqtt", "keycloak"))
        self.assertIn("edol-prod-secure", deployment.command)

    def test_backup_failure_prevents_sql_and_migration(self):
        with tempfile.TemporaryDirectory() as directory:
            deployment = ProductionDeployment(fixture(environment="prod"), Path(directory))
            with patch("dev_deploy.run"), patch.object(deployment, "dc"), \
                    patch.object(deployment, "configure_mqtt_certificate"), patch.object(deployment, "guard_database"), \
                    patch.object(deployment, "backup", side_effect=RuntimeError("backup failed")), \
                    patch.object(deployment, "sql") as sql, patch.object(deployment, "migrate") as migrate:
                with self.assertRaises(RuntimeError):
                    deployment.apply()
            sql.assert_not_called()
            migrate.assert_not_called()

    def test_backup_happens_after_stop_and_ownership_guard_before_migration(self):
        with tempfile.TemporaryDirectory() as directory:
            deployment = ProductionDeployment(fixture(environment="prod"), Path(directory))
            events = []
            with patch("dev_deploy.run"), patch.object(deployment, "dc"), \
                    patch.object(deployment, "configure_mqtt_certificate"), \
                    patch.object(deployment, "stop_instances", side_effect=lambda: events.append("stop")), \
                    patch.object(deployment, "guard_database", side_effect=lambda: events.append("guard")), \
                    patch.object(deployment, "backup", side_effect=lambda: events.append("backup")), \
                    patch.object(deployment, "sql", side_effect=RuntimeError("stop before writing")):
                with self.assertRaises(RuntimeError):
                    deployment.apply()
            self.assertEqual(events, ["stop", "guard", "backup"])

    def test_wrong_environment_label_is_rejected_even_with_correct_volume(self):
        import json
        deployment = ProductionDeployment(fixture(environment="prod"), Path("/state"))
        info = [{"Config": {"Labels": {"com.docker.compose.project": "edol-prod-secure", "dev.edol.environment": "dev"}},
                 "Mounts": [{"Name": "edol-prod-secure_postgres-data", "Destination": "/var/lib/postgresql/data"}]}]
        with patch.object(deployment, "dc", return_value="container"), patch("dev_deploy.run", return_value=json.dumps(info)):
            with self.assertRaises(ValueError):
                deployment.guard_database()

    def test_production_certificates_never_invoke_signing_or_dev_staging(self):
        config = fixture(environment="prod")
        config.update(mqtt_cert_dir="/instance/certs/mqtt", mqtt_truststore="/instance/certs/combined.p12")
        deployment = ProductionDeployment(config, Path("/instance/state"))
        with patch("mqtt_certificate.regular", side_effect=lambda path: path), \
                patch("mqtt_certificate.openssl", return_value=b"same public key") as openssl, \
                patch("mqtt_certificate.stage") as stage, patch("mqtt_certificate.activate") as activate:
            deployment.configure_mqtt_certificate()
            deployment.configure_mqtt_certificate(activate=True)
        stage.assert_not_called()
        activate.assert_not_called()
        self.assertTrue(all(call.args[0] in ("verify", "x509", "pkey") for call in openssl.call_args_list))

    def test_trust_outside_backup_boundary_is_rejected_before_openssl(self):
        deployment = ProductionDeployment(fixture(environment="prod"), Path("/instance/state"))
        with patch("mqtt_certificate.openssl") as openssl:
            with self.assertRaises(ValueError):
                deployment.configure_mqtt_certificate()
        openssl.assert_not_called()


if __name__ == "__main__":
    unittest.main()
