"""Public certificate transport and redeployment checks; no live ACME, Docker or devices."""

import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dev_deploy
import model
import public_tls
from test_deployment import fixture


class PublicModelTests(unittest.TestCase):
    def test_docker_certbot_preserves_existing_sans_and_uses_one_certificate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            certificate = root / "live/edol.example.org/cert.pem"
            certificate.parent.mkdir(parents=True)
            certificate.write_text("public certificate fixture")
            config = dict(fixture(), hub_origin="https://edol.example.org", auth_origin="https://auth.example.org",
                          public_tls={"certbot_config_dir": str(root)})
            extensions = b"X509v3 Subject Alternative Name:\n DNS:edol.example.org, DNS:auth.example.org"
            with patch("public_tls.command", return_value=extensions) as command:
                public_tls.issue_certificates(config, "owner@example.org", Path("/var/www/certbot"), root)
            issuances = [call for call in command.call_args_list if "certonly" in call.args]
            self.assertEqual(len(issuances), 1)
            self.assertEqual(issuances[0].args[:5], ("docker", "compose", "run", "--rm", "certbot"))
            self.assertIn("edol.example.org", issuances[0].args)
            self.assertIn("auth.example.org", issuances[0].args)
            self.assertEqual(issuances[0].kwargs["cwd"], root)

    def test_renewal_registration_preserves_existing_service_and_needs_no_source_checkout(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            units = root / "systemd"
            units.mkdir()
            original = units / "certbot-docker-renew.service"
            original.write_text("[Service]\nExecStartPost=/existing/nginx-reload\n")
            config = dict(fixture(), public_tls={})
            with patch("public_tls.WORKERS", root / "workers"), patch("public_tls.SYSTEMD", units), \
                    patch("public_tls.command") as command:
                public_tls.register_renewal(root / "instance.json", config)
            self.assertIn("/existing/nginx-reload", original.read_text())
            hook = units / "certbot-docker-renew.service.d/edol-edol-dev-secure.conf"
            self.assertIn("--no-block start edol-public-tls-edol-dev-secure.service", hook.read_text())
            refresh = units / "edol-public-tls-edol-dev-secure.service"
            self.assertIn("refresh --config", refresh.read_text())
            self.assertTrue((root / "workers/edol-dev-secure/public_tls.py").is_file())
            command.assert_called_once_with("systemctl", "daemon-reload")

    def test_public_transport_in_both_environments_preserves_identity(self):
        for environment in ("dev", "prod"):
            old = fixture(environment=environment)
            public = copy.deepcopy(old)
            public.update(mqtt_tls="public", mqtt_public_host="edol.example.org", mqtt_public_port=18885)
            self.assertEqual(dev_deploy.persistent_fingerprint(old), dev_deploy.persistent_fingerprint(public))
            result = model.compose(model.validate(public), Path("/source"), Path("/instance/state"))
            self.assertEqual(result["services"]["mqtt"]["networks"]["private"]["aliases"], ["edol.example.org"])
            for name in model.SERVICES:
                service = result["services"][name]
                settings = json.loads(service["environment"]["SPRING_APPLICATION_JSON"])
                self.assertEqual(settings["mqttServer.url"], "ssl://edol.example.org:8883")
                self.assertNotIn("trustStore", str(service))
            core = json.loads(result["services"]["edolcore"]["environment"]["SPRING_APPLICATION_JSON"])
            self.assertEqual(core["edol-core.agent.discovery.mqtt-port"], 18885)

    def test_public_host_and_port_are_validated(self):
        for name in ("mqtt", "192.168.0.200", "https://example.org", "bad..example.org", "-bad.example.org"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                public_tls.host(dict(fixture(), mqtt_public_host=name))
        for value in (0, 65536, True, "8883"):
            with self.assertRaises(ValueError):
                public_tls.port(dict(fixture(), mqtt_public_port=value))

    def test_reload_targets_only_the_named_instance_broker(self):
        with patch("public_tls.command", return_value=b"broker\n") as command:
            public_tls.reload_broker(fixture())
        self.assertIn("label=com.docker.compose.project=edol-dev-secure", command.call_args_list[0].args)
        self.assertEqual(command.call_args_list[1].args, ("docker", "kill", "--signal", "HUP", "broker"))


class PublicCertificateTests(unittest.TestCase):
    def setUp(self):
        self.binary = shutil.which("openssl") or "C:/Program Files/Git/usr/bin/openssl.exe"
        if not Path(self.binary).is_file():
            self.skipTest("OpenSSL is unavailable")
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "source"
        self.source.mkdir()
        self.config = dict(fixture(), mqtt_tls="public", mqtt_public_host="edol.example.org",
                           mqtt_cert_dir=str(self.root / "certs/mqtt"))
        self.ca = self.root / "root.pem"
        self.openssl("req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes",
                     "-subj", "/CN=Isolated root", "-days", "2", "-addext", "basicConstraints=critical,CA:TRUE",
                     "-addext", "keyUsage=critical,keyCertSign,cRLSign", "-keyout", self.root / "root.key", "-out", self.ca)
        self.openssl("req", "-new", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes",
                     "-subj", "/CN=edol.example.org", "-keyout", self.source / "privkey.pem", "-out", self.root / "leaf.csr")
        extension = self.root / "leaf.ext"
        extension.write_text("basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\n"
                             "extendedKeyUsage=serverAuth\nsubjectAltName=DNS:edol.example.org\n")
        self.openssl("x509", "-req", "-in", self.root / "leaf.csr", "-CA", self.ca,
                     "-CAkey", self.root / "root.key", "-set_serial", "2", "-days", "2",
                     "-extfile", extension, "-out", self.source / "fullchain.pem")
        original = subprocess.run
        runner = patch("public_tls.subprocess.run", side_effect=lambda args, **kw: original([self.binary, *args[1:]], **kw))
        runner.start()
        self.addCleanup(runner.stop)

    def openssl(self, *args):
        subprocess.run([self.binary, *map(str, args)], capture_output=True, check=True)

    def test_renewal_preserves_authority_and_rejects_a_bad_replacement(self):
        state = self.root / "state"
        state.mkdir()
        authority = state / "dynamic-security.json"
        authority.write_text("retained authority")
        before = hashlib.sha256(authority.read_bytes()).hexdigest()
        with patch("public_tls.ROOTS", self.ca), patch("public_tls.os.chown", create=True):
            self.assertTrue(public_tls.install(self.config, state, self.source, 1000, 1000))
            public_tls.validate_installed(self.config)
            self.assertFalse(public_tls.install(self.config, state, self.source, 1000, 1000))
            previous = (public_tls.directory(self.config) / "server.pem").read_bytes()
            self.openssl("x509", "-req", "-in", self.root / "leaf.csr", "-CA", self.ca,
                         "-CAkey", self.root / "root.key", "-set_serial", "4", "-days", "2",
                         "-extfile", self.root / "leaf.ext", "-out", self.source / "fullchain.pem")
            self.assertTrue(public_tls.install(self.config, state, self.source, 1000, 1000))
            public_tls.validate_installed(self.config)
            self.assertNotEqual(previous, (public_tls.directory(self.config) / "server.pem").read_bytes())
            # A key/certificate mismatch must not disturb the accepted renewed files.
            self.openssl("x509", "-in", self.source / "fullchain.pem", "-signkey", self.root / "root.key",
                         "-set_serial", "3", "-out", self.root / "untrusted.pem")
            bad = self.root / "bad"
            bad.mkdir()
            shutil.copy(self.root / "untrusted.pem", bad / "fullchain.pem")
            shutil.copy(self.source / "privkey.pem", bad / "privkey.pem")
            previous = (public_tls.directory(self.config) / "server.pem").read_bytes()
            with self.assertRaises(ValueError):
                public_tls.install(self.config, state, bad, 1000, 1000)
            self.assertEqual(previous, (public_tls.directory(self.config) / "server.pem").read_bytes())
        self.assertEqual(before, hashlib.sha256(authority.read_bytes()).hexdigest())

    def test_wrong_hostname_and_unknown_root_are_rejected(self):
        public_tls.verify(self.config, self.source / "fullchain.pem", self.source / "privkey.pem", self.ca)
        for config, roots in ((dict(self.config, mqtt_public_host="wrong.example.org"), self.ca),
                              (self.config, public_tls.ROOTS)):
            with self.assertRaises(ValueError):
                public_tls.verify(config, self.source / "fullchain.pem", self.source / "privkey.pem", roots)


if __name__ == "__main__":
    unittest.main()
