"""Render production/Home compositions and execute explicitly scoped dev deployments."""

import argparse
import hashlib
import http.client
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

import database
import keycloak_smtp
import mqtt_certificate
from model import AMS_READINESS_HEADERS, SERVICES, compose, realm, validate


ROOT = Path(__file__).resolve().parents[2]
DEV_PROJECTS = ("edol-dev-secure", "edol-dev-home")
KNOWN_SERVICES = frozenset(SERVICES) | {"postgres", "mqtt", "keycloak"}
COMPOSE_FILE = "compose.json"


class DeploymentFailure(RuntimeError):
    """Only fixed operation/service identifiers and an HTTP status may reach Actions logs."""

    def __init__(self, stage, services, status=None):
        super().__init__("Deployment operation failed")
        stages = {"http-readiness", "compose-up", "compose-build", "compose-config", "compose-pull",
                  "compose-exec", "compose-ps", "compose-stop", "smtp-config", "mqtt-certificate"}
        self.stage = stage if stage in stages else "deployment"
        self.services = tuple(service for service in services if service in KNOWN_SERVICES)
        self.status = status if isinstance(status, int) and 100 <= status <= 599 else None


def failure_summary(error):
    if isinstance(error, DeploymentFailure):
        status = f" last_http_status={error.status}" if error.status is not None else ""
        return f"Deployment stopped: stage={error.stage} services={','.join(error.services) or 'deployment'}{status}"
    return f"Deployment stopped: {type(error).__name__}. Review the current step and protected configuration."


def run(args, data=None, env=None):
    result = subprocess.run(args, input=data, text=True, capture_output=True, env=env, check=False)
    if result.returncode:
        # Compose, Java and psql diagnostics can contain expanded credentials or SQL.
        raise RuntimeError(f"Command failed ({result.returncode}): {Path(args[0]).name}; inspect locally without exporting secrets")
    return result.stdout


def write_private(path, content):
    if path.is_symlink():
        raise ValueError("Generated files must not be symlinks")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, "O_NOFOLLOW", 0), 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(content)


def persistent_fingerprint(config):
    fields = {k: config.get(k) for k in ("environment", "mode", "project", "secrets", "auth_origin", "keycloak_admin_user",
                                        "mqtt_cert_dir", "mqtt_truststore")}
    return hashlib.sha256(json.dumps(fields, sort_keys=True).encode()).hexdigest()




def prepare(config, state):
    validate_certificates(config)
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    if state.is_symlink() or state.resolve() != state.absolute():
        raise ValueError("Instance state must not traverse symlinks")
    os.chmod(state, 0o700)
    seal = state / "identity.sha256"
    fingerprint = persistent_fingerprint(config)
    if seal.exists() and seal.read_text().strip() != fingerprint:
        raise ValueError("Persistent identity configuration changed; use an explicit reviewed rotation/migration")
    write_private(seal, fingerprint + "\n")
    for directory in ("mqtt", "models", "snapshots", *["logs/" + module for module, _ in SERVICES.values()]):
        path = state / directory
        path.mkdir(parents=True, exist_ok=True, mode=0o700)
        if path.is_symlink():
            raise ValueError("State directories must not be symlinks")
    if config["mode"] == "secure":
        source = json.loads((ROOT / "docker/keycloak/realm/edol-realm.json").read_text())
        import_file = state / "edol-realm.json"
        write_private(import_file, json.dumps(realm(config, source), indent=2))
        # The enclosing host directory is 0700. A file bind mount must also be
        # readable by Keycloak's image UID, which can differ from the operator UID.
        os.chmod(import_file, 0o644)
        authority = state / "mqtt/dynamic-security.json"
        if authority.is_symlink():
            raise ValueError("Broker authority must not be a symlink")
        if not authority.exists():
            spec = importlib.util.spec_from_file_location("dynsec", ROOT / "docker/mosquitto/initialize_security.py")
            dynsec = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(dynsec)
            passwords = {"core-events": "core", "hub-events": "hub", "notify-events": "notify", "ams-events": "ams",
                         "core-agent-control": "control", "core-agent-ingress": "ingress", "core-agent-commands": "command"}
            write_private(authority, json.dumps(dynsec.build_config({u: config["secrets"]["mqtt_" + k] for u, k in passwords.items()})))
        health_options = ["-h mqtt", "-p 8883", "--cafile /mosquitto/certs/ca.pem", "-u hub-events",
                          "-P " + config["secrets"]["mqtt_hub"], "-t edolcore/#", "-i " + config["project"] + "-health"]
        write_private(state / "mqtt-health.conf", "\n".join(health_options) + "\n")
    model = compose(config, ROOT, state)
    destination = state / COMPOSE_FILE
    # Compose also interpolates dollar signs inside JSON strings. Preserve literal secrets.
    write_private(destination, json.dumps(model, indent=2).replace("$", "$$"))
    return model


def validate_certificates(config):
    if config["mode"] == "secure":
        for path in (Path(config["mqtt_cert_dir"]) / "server.pem", Path(config["mqtt_cert_dir"]) / "server-key.pem",
                     Path(config["mqtt_cert_dir"]) / "ca.pem",
                     Path(config["mqtt_truststore"])):
            if not path.is_file():
                raise ValueError("Provision the reviewed MQTT certificates and combined JVM truststore first")


class Deployment:
    def __init__(self, config, state):
        self.config, self.state = config, state
        self.command = ["docker", "compose", "--project-name", config["project"], "-f", str(state / COMPOSE_FILE)]

    def dc(self, *args):
        try:
            return run(self.command + list(args))
        except RuntimeError as error:
            services = tuple(arg for arg in args if arg in KNOWN_SERVICES)
            if args and args[0] == "up":
                services = self.failed_services(services)
            raise DeploymentFailure("compose-" + args[0], services) from error

    def failed_services(self, services):
        failed = []
        for service in services:
            try:
                identities = run(self.command + ["ps", "-a", "-q", service]).split()
                if not identities:
                    failed.append(service)
                    continue
                state = run(["docker", "inspect", "--format",
                             "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}", identities[0]]).strip()
                if state != "running healthy":
                    failed.append(service)
            except RuntimeError:
                return services
        return tuple(failed) or services

    def sql(self, content, database_name="postgres"):
        return run(self.command + ["exec", "-T", "postgres", "psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1",
                                   "-U", "postgres", "-d", database_name], data=content)

    def ip(self, service):
        identity = self.dc("ps", "-q", service).strip()
        if not identity:
            raise RuntimeError("Expected instance service is absent")
        info = json.loads(run(["docker", "inspect", identity]))[0]
        return info["NetworkSettings"]["Networks"][self.config["project"] + "_private"]["IPAddress"]

    def wait_http(self, service, port, path, codes, timeout=180, headers=None):
        address = self.ip(service)
        end = time.monotonic() + timeout
        last_status = None
        while time.monotonic() < end:
            try:
                connection = http.client.HTTPConnection(address, port, timeout=3)
                try:
                    connection.request("GET", path, headers=headers or {})
                    last_status = connection.getresponse().status
                    if last_status in codes:
                        return
                finally:
                    connection.close()
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(2)
        raise DeploymentFailure("http-readiness", (service,), status=last_status)

    def migrate(self, module):
        secure = self.config["mode"] == "secure"
        database_name = "edol_hub" if module == "hub" else "edol_devices"
        user = module + "_flyway" if secure else "home"
        locations = ["filesystem:/migrations/migration"]
        if module == "core" and secure:
            locations.append("filesystem:/migrations/secure-migration")
        if module == "hub" and not secure:
            locations.append("filesystem:/migrations/home-migration")
        env = dict(os.environ)
        env.update({"FLYWAY_URL": f"jdbc:postgresql://{self.ip('postgres')}:5432/{database_name}",
                    "FLYWAY_USER": user, "FLYWAY_PASSWORD": self.config["secrets"][user],
                    "FLYWAY_SCHEMAS": module, "FLYWAY_DEFAULT_SCHEMA": module,
                    "FLYWAY_LOCATIONS": ",".join(locations), "FLYWAY_CLEAN_DISABLED": "true",
                    "FLYWAY_INIT_SQL": f"SET ROLE {module}_schema_owner" if secure else "SELECT 1",
                    "REDGATE_DISABLE_TELEMETRY": "true"})
        command = ["docker", "run", "--rm", "--network", "host"]
        for key in ("FLYWAY_URL", "FLYWAY_USER", "FLYWAY_PASSWORD", "FLYWAY_SCHEMAS", "FLYWAY_DEFAULT_SCHEMA",
                    "FLYWAY_LOCATIONS", "FLYWAY_CLEAN_DISABLED", "FLYWAY_INIT_SQL", "REDGATE_DISABLE_TELEMETRY"):
            command += ["-e", key]
        command += ["-v", f"{ROOT}/edol-{module}/src/main/resources/db:/migrations:ro",
                    self.config["flyway_image"], "migrate"]
        run(command, env=env)


    def provision_database(self):
        self.dc("config", "--quiet")
        self.dc("up", "-d", "--wait", "--wait-timeout", "120", "postgres")
        self.sql(database.provision(self.config))
        for module in ("core", "hub", "ams"):
            if module != "ams" or self.config["mode"] == "secure":
                self.migrate(module)
        self.sql(database.post_migration(self.config))
        print("Empty-database provisioning/migrations complete; existing application data retained.")

    def configure_smtp(self):
        try:
            keycloak_smtp.configure(self.config, self.ip("keycloak"))
        except (OSError, http.client.HTTPException, ValueError, KeyError, TypeError, RuntimeError) as error:
            raise DeploymentFailure("smtp-config", ("keycloak",)) from error

    def configure_mqtt_certificate(self, activate=False):
        try:
            operation = mqtt_certificate.activate if activate else mqtt_certificate.stage
            operation(self.config, self.state)
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            raise DeploymentFailure("mqtt-certificate", ("mqtt",)) from error

    def apply(self, environment="dev"):
        c = self.config
        if c["environment"] != environment or c["project"] != f"edol-{environment}-{c['mode']}":
            raise ValueError("Deployment environment and instance ownership differ")
        run([sys.executable, "-B", "-m", "unittest", "discover", "-s", str(ROOT / "docker/deploy/tests")])
        print("Validating configuration and building the exact supplied source revision", flush=True)
        self.dc("config", "--quiet")
        self.dc("build")
        run(["docker", "pull", c["flyway_image"]])
        self.dc("pull", "--ignore-buildable")
        self.configure_mqtt_certificate()
        self.stop_instances()
        try:
            self.configure_mqtt_certificate(activate=True)
            print("Starting persistent device storage", flush=True)
            self.dc("up", "-d", "--wait", "--wait-timeout", "120", "postgres")
            # Check instance ownership before provisioning or applying migrations.
            self.guard_database()
            self.backup()
            print("Creating missing databases and applying migrations; keeping existing data", flush=True)
            self.sql(database.provision(c))
            for module in ("core", "hub", "ams"):
                if module != "ams" or c["mode"] == "secure":
                    self.migrate(module)
            self.sql(database.post_migration(c))
            if c["mode"] == "secure":
                self.dc("up", "-d", "--wait", "--wait-timeout", "240", "keycloak")
                self.wait_http("keycloak", 9000, "/health/ready", {200})
                print("Applying managed Keycloak SMTP settings", flush=True)
                self.configure_smtp()
            self.dc("up", "-d", "--wait", "--wait-timeout", "120", "mqtt")
            self.dc("up", "-d", "--wait", "--wait-timeout", "240", "edolhub")
            self.wait_http("edolhub", 8090, "/login" if c["mode"] == "secure" else "/", {200, 302})
            self.dc("up", "-d", "--wait", "--wait-timeout", "240", "edolcore", "edolams", "edolnotify")
            self.wait_http("edolcore", 8080, "/api/printers", {401} if c["mode"] == "secure" else {200})
            self.wait_http("edolams", 8099, "/api/terminal/v1/state" if c["mode"] == "secure" else "/ams/state",
                           {401} if c["mode"] == "secure" else {400},
                           headers=AMS_READINESS_HEADERS if c["mode"] == "secure" else None)
            time.sleep(5)
            info = json.loads(run(["docker", "inspect", self.dc("ps", "-q", "edolnotify").strip()]))[0]
            if not info["State"]["Running"] or info["RestartCount"]:
                raise RuntimeError("Notify failed its process-liveness check")
            write_private(self.state / "deployed.json", json.dumps({"revision": c["revision"], "mode": c["mode"]}))
            print("Deploy complete. HTTP readiness passed; Notify process liveness only. Physical acceptance remains separate.")
        except BaseException:
            self.stop_after_failure()
            raise

    def stop_after_failure(self):
        try:
            self.dc("stop")
        except Exception as cleanup_error:
            print("Cleanup failed. " + failure_summary(cleanup_error), file=sys.stderr)

    def stop_instances(self):
        stop_dev_containers()

    def backup(self):
        """Dev retains state; production overrides this with a pre-migration backup."""

    def guard_database(self):
        info = json.loads(run(["docker", "inspect", self.dc("ps", "-q", "postgres").strip()]))[0]
        labels = info["Config"]["Labels"]
        if labels.get("com.docker.compose.project") != self.config["project"] or labels.get("dev.edol.environment") != self.config["environment"]:
            raise ValueError("Refusing deployment: database container ownership mismatch")
        expected_volume = self.config["project"] + "_postgres-data"
        if not any(m.get("Name") == expected_volume and m["Destination"] == "/var/lib/postgresql/data" for m in info["Mounts"]):
            raise ValueError("Refusing deployment: persistent volume ownership mismatch")


def stop_dev_containers():
    for project in DEV_PROJECTS:
        identities = run(["docker", "ps", "-q", "--filter", "label=com.docker.compose.project=" + project]).split()
        if identities:
            run(["docker", "stop", *identities])


def validate_action(args, config):
    if config["mode"] != args.mode:
        raise ValueError("Workflow mode and protected instance configuration differ")
    if args.action == "deploy-dev" and config["environment"] != "dev":
        raise ValueError("Production must use the explicit operator workflow")
    if args.action == "deploy-prod" and config["environment"] != "prod":
        raise ValueError("Production deployment requires a production instance")
    if args.action == "deploy-prod" and (args.source_generation is None or args.source_generation < 1):
        raise ValueError("Production requires the full source history commit count")
    if args.action == "provision-db" and config["environment"] != "dev":
        raise ValueError("Use deploy-prod for production backup and ownership guards")


def execute_locked(args, config, state):
    if config["environment"] == "prod" and (state / "deployed.json").exists() and config["mode"] == "secure" \
            and not (state / "mqtt/dynamic-security.json").is_file():
        raise ValueError("Restore missing broker authority before rendering production state")
    if args.action == "deploy-prod":
        from prod_deploy import validate_generation
        validate_generation(state, args.source_generation, args.revision)
        if (state / COMPOSE_FILE).is_file():
            write_private(state / "compose.previous.json", (state / COMPOSE_FILE).read_text())
    prepare(config, state)
    if args.action == "deploy-dev":
        Deployment(config, state).apply()
    elif args.action == "deploy-prod":
        from prod_deploy import ProductionDeployment
        write_private(state / "source-generation.json", json.dumps({"generation": args.source_generation,
                                                                   "revision": args.revision}))
        ProductionDeployment(config, state).apply()
    elif args.action == "provision-db":
        Deployment(config, state).provision_database()
    else:
        print("Rendered protected Compose configuration. No runtime operation performed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("render", "provision-db", "deploy-dev", "deploy-prod"))
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--mode", choices=("secure", "home"), required=True)
    parser.add_argument("--source-generation", type=int)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.revision):
        raise ValueError("A full source commit SHA is required")
    config = validate(json.loads(args.config.read_text()))
    validate_action(args, config)
    if os.name != "posix":
        raise ValueError("Deployment rendering/execution requires the Linux operator host")
    if args.config.stat().st_mode & 0o077:
        raise ValueError("Protect the instance config with mode 0600")
    config["revision"] = args.revision
    config["uid"], config["gid"] = os.getuid(), os.getgid()
    if args.action != "render":
        validate_local_docker()
    state = args.config.resolve().parent / "state"
    if ROOT in state.parents or state == ROOT:
        raise ValueError("Instance state must be outside the source checkout")
    import fcntl
    # One lock covers secure/home deployments and local invocations, independent of Actions concurrency support.
    lock = args.config.resolve().parent.parent / f".{config['environment']}-deploy.lock"
    with lock.open("a") as handle:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        execute_locked(args, config, state)


def validate_local_docker():
    if any(key in os.environ for key in ("DOCKER_HOST", "DOCKER_CONTEXT")):
        raise ValueError("Remote/overridden Docker endpoints are forbidden for instance deployment")
    endpoint = run(["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"]).strip()
    if endpoint != "unix:///var/run/docker.sock":
        raise ValueError("Deployment must use this host's local Docker socket")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Never print exception text from HTTP/SQL/subprocess libraries carrying secrets.
        print(failure_summary(error), file=sys.stderr)
        sys.exit(1)
