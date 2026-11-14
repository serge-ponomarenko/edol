"""Real OpenSSL certificate regression on disposable local keys only; no live services."""

import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dev_deploy
import mqtt_certificate
from test_deployment import fixture


class MqttCertificateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.binary = shutil.which("openssl") or "C:/Program Files/Git/usr/bin/openssl.exe"
        if not Path(cls.binary).is_file():
            raise unittest.SkipTest("OpenSSL is unavailable")

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.state = self.root / "state"
        self.directory = self.root / "certs/mqtt"
        self.authority = self.root / "ca"
        for path in (self.state, self.directory, self.authority):
            path.mkdir(parents=True)
        self.config = fixture()
        self.config["mqtt_cert_dir"] = str(self.directory)
        original_run = subprocess.run

        def run(args, **kwargs):
            return original_run([self.binary, *args[1:]], **kwargs)

        self.runner = patch("mqtt_certificate.subprocess.run", side_effect=run)
        self.runner.start()
        self.addCleanup(self.runner.stop)
        self.command("req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes",
                     "-subj", "/CN=Disposable MQTT test CA", "-days", "2", "-keyout", self.authority / "ca-key.pem",
                     "-out", self.directory / "ca.pem")
        self.command("req", "-new", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes",
                     "-subj", "/CN=mqtt", "-keyout", self.directory / "server-key.pem", "-out", self.root / "server.csr")
        extension = self.root / "server.ext"
        extension.write_text("subjectAltName=DNS:mqtt,DNS:retained.test,IP:192.168.0.200\nextendedKeyUsage=serverAuth\n")
        self.command("x509", "-req", "-in", self.root / "server.csr", "-CA", self.directory / "ca.pem",
                     "-CAkey", self.authority / "ca-key.pem", "-set_serial", "19", "-days", "1",
                     "-extfile", extension, "-out", self.directory / "server.pem")
        (self.state / "identity.sha256").write_text("preserved-identity-seal")
        (self.state / "dynamic-security.json").write_text("preserved-agent-authority")
        self.protected = [self.directory / "ca.pem", self.authority / "ca-key.pem", self.directory / "server-key.pem",
                          self.state / "identity.sha256", self.state / "dynamic-security.json"]
        self.before = self.digests()

    @staticmethod
    def command(*args):
        return mqtt_certificate.openssl(*args)

    def digests(self):
        return [hashlib.sha256(path.read_bytes()).hexdigest() for path in self.protected]

    def test_stage_preserves_active_certificate_ca_keys_authority_and_adds_public_san(self):
        current = (self.directory / "server.pem").read_bytes()
        pending = mqtt_certificate.stage(self.config, self.state)
        self.assertEqual((self.directory / "server.pem").read_bytes(), current)
        self.assertEqual(self.digests(), self.before)
        mqtt_certificate.verify(self.config, self.state, pending)
        self.command("verify", "-CAfile", self.directory / "ca.pem", "-verify_hostname", "retained.test", pending)
        self.assertEqual(mqtt_certificate.stage(self.config, self.state), pending)

    def test_activation_preserves_ca_keys_authority_and_is_idempotent(self):
        previous = (self.directory / "server.pem").read_bytes()
        mqtt_certificate.stage(self.config, self.state)
        mqtt_certificate.activate(self.config, self.state)
        mqtt_certificate.verify(self.config, self.state, self.directory / "server.pem")
        active = (self.directory / "server.pem").read_bytes()
        mqtt_certificate.activate(self.config, self.state)
        self.assertEqual(active, (self.directory / "server.pem").read_bytes())
        self.assertEqual(self.digests(), self.before)
        self.assertEqual(next(self.state.glob("mqtt-server.previous-*.pem")).read_bytes(), previous)

    def test_invalid_candidate_never_replaces_live_certificate(self):
        previous = (self.directory / "server.pem").read_bytes()
        (self.state / "mqtt-server.pending.pem").write_text("invalid public certificate")
        with self.assertRaises(ValueError):
            mqtt_certificate.activate(self.config, self.state)
        self.assertEqual((self.directory / "server.pem").read_bytes(), previous)
        self.assertEqual(self.digests(), self.before)

    def test_missing_signer_never_regenerates_ca_or_server_key(self):
        (self.authority / "ca-key.pem").rename(self.authority / "offline-ca-key.pem")
        with self.assertRaises(ValueError):
            mqtt_certificate.stage(self.config, self.state)
        self.assertFalse((self.state / "mqtt-server.pending.pem").exists())

    def test_valid_active_certificate_does_not_require_ca_private_key_online(self):
        mqtt_certificate.activate(self.config, self.state)
        (self.authority / "ca-key.pem").rename(self.authority / "offline-ca-key.pem")
        self.assertIsNone(mqtt_certificate.stage(self.config, self.state))

    def test_home_does_not_use_openssl_and_production_cannot_sign(self):
        with patch("mqtt_certificate.openssl") as command:
            mqtt_certificate.stage(fixture("home"), self.state)
            mqtt_certificate.activate(fixture("home"), self.state)
            with self.assertRaises(ValueError):
                mqtt_certificate.stage(fixture(environment="prod"), self.state)
            command.assert_not_called()

    def test_failed_certificate_step_reports_only_safe_stage(self):
        deployment = dev_deploy.Deployment(self.config, self.state)
        with patch("mqtt_certificate.stage", side_effect=ValueError("synthetic sensitive diagnostics")):
            with self.assertRaises(dev_deploy.DeploymentFailure) as captured:
                deployment.configure_mqtt_certificate()
        self.assertEqual(dev_deploy.failure_summary(captured.exception),
                         "Deployment stopped: stage=mqtt-certificate services=mqtt")


class CertificateDeploymentOrderTests(unittest.TestCase):
    def test_stage_failure_does_not_stop_the_existing_dev(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        failure = dev_deploy.DeploymentFailure("mqtt-certificate", ("mqtt",))
        with patch("dev_deploy.run"), patch.object(deployment, "dc"), \
                patch.object(deployment, "configure_mqtt_certificate", side_effect=failure), \
                patch("dev_deploy.stop_dev_containers") as stop:
            with self.assertRaises(dev_deploy.DeploymentFailure):
                deployment.apply()
        stop.assert_not_called()

    def test_activation_occurs_after_stop_and_failure_prevents_service_start(self):
        deployment = dev_deploy.Deployment(fixture(), Path("/state"))
        events = []

        def certificate(activate=False):
            events.append("activate" if activate else "stage")
            if activate:
                raise dev_deploy.DeploymentFailure("mqtt-certificate", ("mqtt",))

        with patch("dev_deploy.run"), patch.object(deployment, "dc") as command, \
                patch("dev_deploy.stop_dev_containers", side_effect=lambda: events.append("stop")), \
                patch.object(deployment, "configure_mqtt_certificate", side_effect=certificate):
            with self.assertRaises(dev_deploy.DeploymentFailure):
                deployment.apply()
        self.assertEqual(events, ["stage", "stop", "activate"])
        self.assertFalse(any(call.args[0] == "up" for call in command.call_args_list))


if __name__ == "__main__":
    unittest.main()
