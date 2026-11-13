"""Disposable Keycloak/PostgreSQL regression; never uses an instance config or existing volume.

Run with the remote-Docker skill wrapper when local Docker is unavailable.
Only synthetic SMTP credentials are used; no external email is sent.
"""

import base64
import http.client
import json
from pathlib import Path
import socketserver
import subprocess
import sys
import tempfile
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import keycloak_smtp


CONFIG = {"keycloak_admin_user": "test-admin", "secrets": {"keycloak_admin": "synthetic-admin-only"},
          "smtp": {"host": "smtp-fixture", "port": "2525", "from": "sender@example.test",
                   "user": "smtp-test", "password": "synthetic-first", "auth": True, "starttls": False}}
PASSWORD_FILE = Path("/tmp/smtp-fixture-password")


class SmtpHandler(socketserver.StreamRequestHandler):
    def handle(self):
        authenticated = False
        self.wfile.write(b"220 disposable SMTP\r\n")
        while line := self.rfile.readline():
            command = line.decode().strip()
            verb = command.split(" ", 1)[0].upper()
            if verb in ("EHLO", "HELO"):
                self.wfile.write(b"250-disposable\r\n250 AUTH PLAIN LOGIN\r\n")
            elif verb == "AUTH":
                parts = command.split()
                if parts[1].upper() == "PLAIN":
                    if len(parts) < 3:
                        self.wfile.write(b"334 \r\n")
                        parts.append(self.rfile.readline().strip().decode())
                    _, user, password = base64.b64decode(parts[2]).decode().split("\0")
                else:
                    self.wfile.write(b"334 VXNlcm5hbWU6\r\n")
                    user = base64.b64decode(self.rfile.readline()).decode()
                    self.wfile.write(b"334 UGFzc3dvcmQ6\r\n")
                    password = base64.b64decode(self.rfile.readline()).decode()
                authenticated = user == "smtp-test" and password == PASSWORD_FILE.read_text()
                self.wfile.write(b"235 authenticated\r\n" if authenticated else b"535 rejected\r\n")
            elif verb == "MAIL" and not authenticated:
                self.wfile.write(b"530 Error: authentication Required\r\n")
            elif verb == "DATA":
                self.wfile.write(b"354 send data\r\n")
                while self.rfile.readline().strip() != b".":
                    pass
                self.wfile.write(b"250 accepted\r\n")
            elif verb == "QUIT":
                self.wfile.write(b"221 bye\r\n")
                return
            else:
                self.wfile.write(b"250 OK\r\n")


def api(method, path, data=None, expected=200):
    token = keycloak_smtp.admin_token(CONFIG, "keycloak")
    result = keycloak_smtp.request("keycloak", method, path, json.dumps(data) if data is not None else None,
                                   {"Authorization": "Bearer " + token, "Content-Type": "application/json"}, expected)
    return json.loads(result) if result else None


def wait_ready():
    end = time.monotonic() + 240
    while time.monotonic() < end:
        try:
            keycloak_smtp.admin_token(CONFIG, "keycloak")
            return
        except (OSError, http.client.HTTPException, ValueError, KeyError, RuntimeError):
            time.sleep(2)
    raise RuntimeError("Disposable Keycloak readiness timed out")


def send_verification(expected):
    user = api("GET", "/admin/realms/edol/users?username=retained-user")[0]
    api("PUT", "/admin/realms/edol/users/" + user["id"] + "/execute-actions-email",
        ["VERIFY_EMAIL"], expected)


def check_preserved():
    realm = api("GET", "/admin/realms/edol")
    if realm["displayName"] != "Retained operator setting" or realm["registrationAllowed"] is not False:
        raise RuntimeError("Unrelated realm settings changed")
    if len(api("GET", "/admin/realms/edol/users?username=retained-user")) != 1:
        raise RuntimeError("User was not preserved")
    if len(api("GET", "/admin/realms/edol/clients?clientId=retained-client")) != 1:
        raise RuntimeError("Client was not preserved")
    api("GET", "/admin/realms/edol/roles/retained-role")


def inside(phase):
    wait_ready()
    if phase == "first":
        api("PUT", "/admin/realms/edol", {"displayName": "Retained operator setting", "registrationAllowed": False}, 204)
        api("POST", "/admin/realms/edol/users", {"username": "retained-user", "enabled": True,
            "email": "recipient@example.test", "emailVerified": False}, 201)
        api("POST", "/admin/realms/edol/clients", {"clientId": "retained-client"}, 201)
        api("POST", "/admin/realms/edol/roles", {"name": "retained-role"}, 201)
        # Imported Python-style "True" disables AUTH. The SMTP sink rejects MAIL with 530.
        send_verification(500)
        keycloak_smtp.configure(CONFIG, "keycloak")
        send_verification(204)
    else:
        # Startup import must leave the previously configured SMTP map unchanged.
        if api("GET", "/admin/realms/edol")["smtpServer"]["auth"] != "true":
            raise RuntimeError("Existing realm was replaced by startup import")
        CONFIG["smtp"]["password"] = "synthetic-rotated+$%&=key"
        PASSWORD_FILE.write_text(CONFIG["smtp"]["password"])
        # Persisted old credentials must fail until the deployment synchronizes SMTP.
        send_verification(500)
        keycloak_smtp.configure(CONFIG, "keycloak")
        send_verification(204)
        keycloak_smtp.configure(CONFIG, "keycloak")
        send_verification(204)
    check_preserved()
    print("PASS: " + phase + " SMTP application, delivery and retained realm sentinels")


def docker(*args, data=None):
    result = subprocess.run(["docker", *args], input=data, text=True, capture_output=True, check=False, timeout=300)
    if result.returncode:
        raise RuntimeError("Disposable Docker operation failed")
    return result.stdout.strip()


def smoke():
    prefix = "edol-smtp-test-" + uuid.uuid4().hex[:10]
    resources = []
    docker("network", "create", prefix)
    try:
        for alias, image, options, command in (
            ("postgres", "postgres:17", ["-e", "POSTGRES_PASSWORD=synthetic-db-only"], []),
            ("smtp-fixture", "python:3.11-alpine", [], ["sleep", "1200"]),
            ("keycloak", "quay.io/keycloak/keycloak:26.7.4",
             ["-e", "KC_BOOTSTRAP_ADMIN_USERNAME=" + CONFIG["keycloak_admin_user"],
              "-e", "KC_BOOTSTRAP_ADMIN_PASSWORD=" + CONFIG["secrets"]["keycloak_admin"],
              "-e", "KC_DB=postgres", "-e", "KC_DB_URL=jdbc:postgresql://postgres:5432/postgres",
              "-e", "KC_DB_USERNAME=postgres", "-e", "KC_DB_PASSWORD=synthetic-db-only"],
             ["start-dev", "--import-realm"])):
            name = prefix + "-" + alias
            docker("create", "--name", name, "--label", "dev.edol.test=smtp", "--network", prefix,
                   "--network-alias", alias, *options, image, *command)
            resources.append(name)
        runner, keycloak = prefix + "-smtp-fixture", prefix + "-keycloak"
        directory = Path(__file__).resolve().parent
        for source in (directory / "keycloak_smtp_smoke.py", directory.parent / "keycloak_smtp.py", directory.parent / "model.py"):
            docker("cp", str(source), runner + ":/tmp/" + source.name)
        broken_smtp = {k: str(v) for k, v in CONFIG["smtp"].items()}
        with tempfile.TemporaryDirectory() as directory:
            import_dir = Path(directory) / "import"
            import_dir.mkdir()
            path = import_dir / "edol-realm.json"
            path.write_text(json.dumps({"realm": "edol", "enabled": True, "sslRequired": "none",
                                        "smtpServer": broken_smtp, "registrationAllowed": True}))
            docker("cp", str(import_dir), keycloak + ":/opt/keycloak/data/")
        docker("start", resources[0], runner)
        docker("exec", "-d", runner, "python", "/tmp/keycloak_smtp_smoke.py", "--smtp")
        docker("start", keycloak)
        print(docker("exec", runner, "python", "/tmp/keycloak_smtp_smoke.py", "--inside", "first"))
        if "530 Error: authentication Required" not in docker("logs", keycloak):
            raise RuntimeError("Expected unauthenticated SMTP 530 was not reproduced")
        docker("restart", keycloak)
        print(docker("exec", runner, "python", "/tmp/keycloak_smtp_smoke.py", "--inside", "repeat"))
    finally:
        for resource in reversed(resources):
            docker("rm", "-f", "-v", resource)
        docker("network", "rm", prefix)


if __name__ == "__main__":
    try:
        if "--smtp" in sys.argv:
            PASSWORD_FILE.write_text(CONFIG["smtp"]["password"])
            with socketserver.ThreadingTCPServer(("0.0.0.0", 2525), SmtpHandler) as server:
                server.serve_forever()
        elif "--inside" in sys.argv:
            inside(sys.argv[-1])
        else:
            smoke()
    except Exception:
        # Never export raw Keycloak, SMTP, Docker or HTTP diagnostics.
        print("FAIL: disposable SMTP verification; sensitive diagnostics suppressed", file=sys.stderr)
        sys.exit(1)
