"""Production lifecycle: scoped stop, consistent backup, then immutable migrations."""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
from urllib.parse import urlsplit

from dev_deploy import Deployment, DeploymentFailure, write_private
import mqtt_certificate


def validate_generation(state, generation, revision):
    """Reject an older queued main revision under the production host lock."""
    marker = state / "source-generation.json"
    if marker.exists():
        previous = json.loads(marker.read_text())
        if generation < previous["generation"] or (generation == previous["generation"] and revision != previous["revision"]):
            raise ValueError("Older or divergent source history cannot replace production")


class ProductionDeployment(Deployment):
    def apply(self, environment="prod"):
        if environment != "prod" or self.config["environment"] != "prod":
            raise ValueError("Production action requires a production instance")
        if (self.state / "deployed.json").exists() and self.config["mode"] == "secure" \
                and not (self.state / "mqtt/dynamic-security.json").is_file():
            raise ValueError("Restore missing broker authority before deploying")
        super().apply(environment="prod")

    def stop_instances(self):
        # Never stop Dev or the legacy installation. PostgreSQL stays available for backup.
        services = ["edolcore", "edolhub", "edolams", "edolnotify", "mqtt"]
        if self.config["mode"] == "secure":
            services.append("keycloak")
        self.dc("stop", *services)

    def configure_mqtt_certificate(self, activate=False):
        if self.config["mode"] != "secure" or activate:
            return
        # Production validates operator-provisioned trust; it never signs or rotates it.
        directory = Path(self.config["mqtt_cert_dir"])
        for path in (directory, Path(self.config["mqtt_truststore"])):
            if not path.resolve().is_relative_to(self.state.parent.resolve()):
                raise ValueError("Production trust must be inside the backed-up instance directory")
        certificate = mqtt_certificate.regular(directory / "server.pem")
        authority = mqtt_certificate.regular(directory / "ca.pem")
        key = mqtt_certificate.regular(directory / "server-key.pem")
        try:
            for option, identity in (("-verify_hostname", "mqtt"),
                                     ("-verify_hostname", urlsplit(self.config["hub_origin"]).hostname),
                                     ("-verify_ip", self.config["bind_address"])):
                mqtt_certificate.openssl("verify", "-purpose", "sslserver", "-CAfile", authority,
                                         option, identity, certificate)
            if mqtt_certificate.openssl("x509", "-in", certificate, "-noout", "-pubkey") != \
                    mqtt_certificate.openssl("pkey", "-in", key, "-pubout"):
                raise ValueError("MQTT key mismatch")
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            raise DeploymentFailure("mqtt-certificate", ("mqtt",)) from error

    def backup(self):
        root = self.state.parent.parent / "backups"
        root.mkdir(mode=0o700, exist_ok=True)
        if root.is_symlink() or root.resolve() != root.absolute():
            raise ValueError("Backup root must not traverse symlinks")
        os.chmod(root, 0o700)
        destination = root / f"{time.time_ns()}-{self.config['mode']}-{self.config['revision']}"
        destination.mkdir(mode=0o700)
        self.capture(destination / "globals.sql", self.command + ["exec", "-T", "postgres",
                     "pg_dumpall", "-U", "postgres", "--globals-only"])
        existing = self.sql("SELECT datname FROM pg_database WHERE datname IN "
                            "('edol_devices','edol_hub','edol_keycloak');").split()
        expected = {"edol_devices", "edol_hub"}
        if self.config["mode"] == "secure":
            expected.add("edol_keycloak")
        if (self.state / "deployed.json").exists() and not expected.issubset(existing):
            raise ValueError("Previously deployed databases are missing; restore before deploying")
        for name in existing:
            self.capture(destination / (name + ".dump"), self.command + ["exec", "-T", "postgres",
                         "pg_dump", "-U", "postgres", "-Fc", "-d", name])
        # Includes config, keys, broker authority and media while every writer is stopped.
        self.capture(destination / "instance.tar.gz", ["tar", "-czf", "-", "-C",
                     str(self.state.parent.parent), self.state.parent.name])
        manifest = {}
        for path in destination.iterdir():
            with path.open("rb") as stream:
                manifest[path.name] = hashlib.file_digest(stream, "sha256").hexdigest()
        write_private(destination / "complete.json", json.dumps({"target_revision": self.config["revision"],
                                                                "sha256": manifest}, indent=2))
        print("Protected pre-migration backup complete; restore acceptance remains separate.", flush=True)

    @staticmethod
    def capture(destination, command):
        descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            result = subprocess.run(command, stdout=stream, stderr=subprocess.PIPE, check=False, timeout=600)
        if result.returncode:
            raise RuntimeError("Protected backup failed; migration must not proceed")
