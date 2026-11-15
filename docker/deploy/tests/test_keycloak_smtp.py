"""SMTP rendering, narrow updates and fail-closed deployment checks; synthetic data only."""

import http.client
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dev_deploy
import keycloak_smtp
import model
from test_deployment import fixture


class KeycloakSmtpTests(unittest.TestCase):
    def test_keycloak_http_failure_preserves_only_status_in_deployment_summary(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        with patch.object(deployment, "ip", return_value="keycloak"), \
                patch("keycloak_smtp.configure", side_effect=keycloak_smtp.KeycloakRequestFailure(403)):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.configure_smtp()
        self.assertEqual(dev_deploy.failure_summary(captured.exception),
                         "Deployment stopped: stage=smtp-config services=keycloak last_http_status=403")

    def test_boolean_flags_render_as_keycloak_lowercase_strings(self):
        source = json.loads((dev_deploy.ROOT / "docker/keycloak/realm/edol-realm.json").read_text())
        for value, expected in ((True, "true"), (False, "false"), ("TRUE", "true"), ("false", "false")):
            config = fixture()
            config["smtp"].update(auth=value, starttls=value, password="synthetic+$%&=key")
            rendered = model.realm(config, source)["smtpServer"]
            self.assertEqual(rendered, model.smtp_settings(config))
            self.assertEqual(rendered["auth"], expected)
            self.assertEqual(rendered["starttls"], expected)
            if rendered["password"] != config["smtp"]["password"]:
                self.fail("SMTP password changed")

    def test_invalid_flags_are_rejected_without_echoing_values(self):
        config = fixture()
        config["smtp"]["auth"] = "synthetic-sensitive-invalid"
        with self.assertRaises(ValueError) as captured:
            model.smtp_settings(config)
        self.assertNotIn("synthetic-sensitive", str(captured.exception))

    def test_each_application_updates_only_smtp_with_current_password(self):
        config = fixture()
        original_fingerprint = dev_deploy.persistent_fingerprint(config)
        with patch("keycloak_smtp.request", return_value=b'{"access_token":"synthetic-token"}') as request:
            for password in ("synthetic-old-key", "synthetic-new+$%&=key"):
                config["smtp"]["password"] = password
                keycloak_smtp.configure(config, "keycloak")
                args = request.call_args.args
                self.assertEqual(args[:3], ("keycloak", "PUT", "/admin/realms/edol"))
                self.assertEqual(set(json.loads(args[3])), {"smtpServer"})
                if json.loads(args[3])["smtpServer"]["password"] != password:
                    self.fail("Current SMTP password was not sent")
            self.assertEqual(request.call_count, 4)
        self.assertEqual(dev_deploy.persistent_fingerprint(config), original_fingerprint)

    def test_admin_error_does_not_read_or_expose_response_body_and_closes_connection(self):
        for status in (301, 400, 401, 403, 404, 500):
            connection = Mock()
            connection.getresponse.return_value.status = status
            with patch("keycloak_smtp.http.client.HTTPConnection", return_value=connection):
                with self.assertRaises(RuntimeError):
                    keycloak_smtp.request("keycloak", "PUT", "/admin/realms/edol", "", {}, 204)
            connection.getresponse.return_value.read.assert_not_called()
            connection.close.assert_called_once()

    def test_smtp_stage_failure_has_only_safe_identifiers(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        for error in (OSError("synthetic-sensitive"), http.client.BadStatusLine("synthetic-sensitive"),
                      ValueError("synthetic-sensitive"), RuntimeError("synthetic-sensitive")):
            with patch.object(deployment, "ip", return_value="keycloak"), \
                    patch("keycloak_smtp.configure", side_effect=error):
                with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                    deployment.configure_smtp()
            self.assertEqual(dev_deploy.failure_summary(captured.exception),
                             "Deployment stopped: stage=smtp-config services=keycloak")

    def test_invalid_token_never_updates_realm(self):
        for response in (b'{}', b'not-json', b'{"access_token":null}', b'{"access_token":""}'):
            with patch("keycloak_smtp.request", return_value=response) as request:
                with self.assertRaises((KeyError, ValueError)):
                    keycloak_smtp.configure(fixture(), "keycloak")
                request.assert_called_once()

    def test_secure_apply_orders_smtp_before_apps_and_stops_on_failure(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        events = []
        failure = dev_deploy.DeploymentFailure("smtp-config", ("keycloak",))

        def command(*args):
            events.append(args)
            return ""

        def ready(*args, **kwargs):
            events.append(("ready", args[0]))

        def configure():
            events.append(("smtp",))
            raise failure

        with patch("dev_deploy.run"), patch("dev_deploy.stop_dev_containers"), \
                patch.object(deployment, "dc", side_effect=command), patch.object(deployment, "sql"), \
                patch.object(deployment, "guard_database"), patch.object(deployment, "migrate"), \
                patch.object(deployment, "wait_http", side_effect=ready), \
                patch.object(deployment, "configure_mqtt_certificate"), \
                patch.object(deployment, "configure_smtp", side_effect=configure):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.apply()
        self.assertIs(captured.exception, failure)
        self.assertEqual(events[-3:], [("ready", "keycloak"), ("smtp",), ("stop",)])
        self.assertFalse(any("edolhub" in event for event in events))

    def test_home_apply_never_configures_keycloak(self):
        deployment = dev_deploy.Deployment(fixture("home"), Path("/state"))
        with patch("dev_deploy.run", return_value='[{"State":{"Running":true},"RestartCount":0}]'), \
                patch("dev_deploy.stop_dev_containers"), patch("dev_deploy.write_private"), \
                patch("dev_deploy.time.sleep"), patch.object(deployment, "dc", return_value="container"), \
                patch.object(deployment, "sql"), patch.object(deployment, "guard_database"), \
                patch.object(deployment, "migrate"), patch.object(deployment, "wait_http"), \
                patch.object(deployment, "configure_smtp") as configure:
            deployment.apply()
        configure.assert_not_called()


if __name__ == "__main__":
    unittest.main()
