"""Bounded Stage 9 resume diagnostics and explicit, non-destructive repair preparation.

Run on the existing Debian Docker host. No database access, MQTT publication,
device access, deployment, resource deletion, or raw log/config dumping.
"""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

PROJECT = "edol-stage9-hardware"
JSON_MEDIA_TYPE = "application/json"
AGENT_MANAGE_SCOPE = "core.agent.manage"
CLIENT_SCOPES_PATH = "/client-scopes"
HUB_SCOPES = ("tenant.context core.printer.read core.printer.manage core.state.read "
              "core.command.execute core.media.read ams.terminal.manage core.agent.manage")
AUDIENCES = {"edol-hub-service": ["edol-core-api", "edol-ams-api"],
             "edol-ams-service": ["edol-core-api", "edol-hub-api"],
             "edol-notify-service": ["edol-core-api"]}


class DiagnosticError(Exception):
    """An intentionally sanitized error suitable for the operator report."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def command(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=30, check=False)
    if result.returncode:
        raise DiagnosticError("Command failed: " + args[0] + " " + args[1])
    return result.stdout


def containers():
    names = command(["docker", "ps", "-a", "--filter",
                     "label=com.docker.compose.project=" + PROJECT, "--format", "{{.ID}}"]).split()
    if not names:
        raise DiagnosticError("Existing Stage 9 project not found; refusing to bootstrap a replacement")
    rows = json.loads(command(["docker", "inspect"] + names))
    return {r["Config"]["Labels"]["com.docker.compose.service"]: r for r in rows}


def environment(row):
    return {key: value for e in row["Config"].get("Env", [])
            for key, _, value in [e.partition("=")] if "=" in e}


def address(row, port):
    networks = row["NetworkSettings"]["Networks"].values()
    ips = [n["IPAddress"] for n in networks if n.get("IPAddress")]
    if len(ips) != 1:
        raise DiagnosticError("Expected one active private service network")
    return "http://" + ips[0] + ":" + str(port)


def request(url, token=None, data=None, method=None, tenant=None, context=None):
    headers = {"Accept": JSON_MEDIA_TYPE}
    if token:
        headers["Authorization"] = "Bearer " + token
    if tenant:
        headers["X-EDOL-Tenant-Id"] = tenant
    if isinstance(data, dict):
        data = json.dumps(data).encode()
        headers["Content-Type"] = JSON_MEDIA_TYPE
    elif data is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=context))
        with opener.open(req, timeout=12) as response:
            payload = response.read(262145)
            if len(payload) > 262144:
                raise DiagnosticError("Response exceeded diagnostic limit")
            return response.status, json.loads(payload) if payload and response.headers.get_content_type() == JSON_MEDIA_TYPE else None
    except urllib.error.HTTPError as error:
        # Never display the response body, URL, token, environment or exception text.
        return error.code, None


def service_token(env, scopes):
    issuer = env["EDOL_KEYCLOAK_ISSUER_URI"].rstrip("/")
    form = urllib.parse.urlencode({"grant_type": "client_credentials",
                                  "client_id": env.get("EDOL_HUB_SERVICE_CLIENT_ID", "edol-hub-service"),
                                  "client_secret": env["EDOL_HUB_SERVICE_CLIENT_SECRET"],
                                  "scope": scopes}).encode()
    status, body = request(issuer + "/protocol/openid-connect/token", data=form)
    if status != 200 or not body or "access_token" not in body:
        raise DiagnosticError("Hub service token failed (HTTP " + str(status) + "); check client scopes/credentials")
    return body["access_token"]


def claim_summary(token):
    # Diagnostic summary only; authorization is tested by the actual resource servers.
    claims = json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "==="))
    aud = claims.get("aud", [])
    return {"issuer": claims.get("iss"), "audiences": [aud] if isinstance(aud, str) else aud,
            "scopes": sorted(claims.get("scope", "").split())}


def check(args, rows):
    result = {"time": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "project": PROJECT, "hardware": "NOT RUN", "checks": {}}
    checks = result["checks"]
    checks["containers"] = {s: r["State"]["Status"] for s, r in rows.items()}
    if not all(rows.get(s, {}).get("State", {}).get("Running") for s in
               ["postgres", "keycloak", "mqtt", "edolcore", "edolhub", "edolams", "edolnotify"]):
        checks["startup"] = "BLOCKED: one or more existing services are not running"
    for service, key in [("edolhub", "EDOL_HUB_AGENT_ENABLED"), ("edolcore", "EDOL_CORE_AGENT_ENABLED"),
                         ("edolcore", "EDOL_CORE_AGENT_MQTT_ENABLED")]:
        checks[key] = environment(rows[service]).get(key, "not set; inspect effective external config if used")
    try:
        env = environment(rows["edolhub"])
        token = service_token(env, HUB_SCOPES)
        summary = claim_summary(token)
        checks["hub_service_token"] = summary
        checks["required_authority"] = "PASS" if (
            set(AUDIENCES["edol-hub-service"]).issubset(summary["audiences"])
            and set(HUB_SCOPES.split()).issubset(summary["scopes"])) else "BLOCKED: missing audience/scope"
        for name, row, port, path in [
            ("printer", rows["edolcore"], 8080, "/api/printers/" + args.printer),
            ("agent_status", rows["edolcore"], 8080, "/api/printers/" + args.printer + "/agent"),
            ("terminal_status", rows["edolams"], 8099, "/internal/terminals/" + args.printer),
        ]:
            status, body = request(address(row, port) + path, token=token, tenant=args.tenant)
            checks[name] = {"http": status}
            if name == "printer" and status == 200 and body:
                checks[name].update({k: body.get(k) for k in ["printerId", "enabled", "connectionMode"]})
            # A 404 is legitimate for a known printer with no enrolled terminal/Agent.
    except (DiagnosticError, KeyError, ValueError, urllib.error.URLError) as error:
        checks["service_path"] = str(error) if isinstance(error, DiagnosticError) else "BLOCKED: service connection/configuration failed"
    try:
        context = ssl.create_default_context(cafile=str(args.run_dir / "certs/ca.pem"))
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        status, _ = request("https://" + args.host + ":18443/api/agent/v1/enroll", context=context)
        checks["agent_https"] = {"tls": "verified", "http": status,
                                 "note": "Connectivity only; enrollment is NOT RUN"}
    except OSError:
        checks["agent_https"] = "BLOCKED: CA/SAN/expiry or listener connectivity; no insecure retry"
    return result


def ready(report):
    checks = report["checks"]
    return ("startup" not in checks
            and checks.get("required_authority") == "PASS"
            and checks.get("printer", {}).get("http") == 200
            and checks.get("printer", {}).get("connectionMode") == "AGENT"
            and checks.get("agent_status", {}).get("http") in [200, 204, 404]
            and checks.get("terminal_status", {}).get("http") in [200, 404]
            and isinstance(checks.get("agent_https"), dict)
            and checks["agent_https"].get("http") in [403, 405]
            and all(checks.get(key, "").lower() == "true" for key in
                    ["EDOL_HUB_AGENT_ENABLED", "EDOL_CORE_AGENT_ENABLED", "EDOL_CORE_AGENT_MQTT_ENABLED"]))


def write_new(path, content):
    # Never overwrite earlier candidates, configuration or authority state.
    with path.open("x", encoding="utf-8") as output:
        os.chmod(path, 0o600)
        output.write(content)


def prepare(args, rows):
    version = command(["docker", "compose", "version", "--short"]).lstrip("v")
    numbers = re.match(r"(\d+)\.(\d+)\.(\d+)", version)
    if not numbers or tuple(map(int, numbers.groups())) < (2, 24, 4):
        raise DiagnosticError("Compose >= 2.24.4 is required for safe port replacement")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", args.image_tag or ""):
        raise DiagnosticError("Specify --image-tag for locally built repaired images")
    issuer = environment(rows["edolhub"])["EDOL_KEYCLOAK_ISSUER_URI"]
    # Nonsecret-only override; existing env files, volumes and keys remain in the base.
    output = args.run_dir / "remediation"
    output.mkdir(mode=0o700, exist_ok=True)
    lines = ["# Generated candidate only; not applied by this script.", "services:"]
    for service in ["edolcore", "edolhub", "edolams", "edolnotify"]:
        name = {"edolcore": "core", "edolhub": "hub", "edolams": "ams", "edolnotify": "notify"}[service]
        lines += ["  " + service + ":"]
        lines += ["    image: edol-stage9-" + name + ":" + args.image_tag]
        lines += ["    environment:", "      EDOL_KEYCLOAK_ISSUER_URI: " + json.dumps(issuer)]
        if service == "edolhub":
            lines += ["      EDOL_HUB_AGENT_ENABLED: 'true'", "      EDOL_AMS_URL: http://edolams:8099",
                      "      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_EDOL_HUB_SERVICE_SCOPE: " + HUB_SCOPES.replace(" ", ",")]
        if service == "edolcore":
            lines += ["      EDOL_CORE_AGENT_ENABLED: 'true'", "      EDOL_CORE_AGENT_MQTT_ENABLED: 'true'",
                      "      EDOL_CORE_TRUSTED_SERVICE_CLIENT_IDS: edol-hub-service,edol-ams-service,edol-notify-service",
                      "      SERVER_FORWARD_HEADERS_STRATEGY: framework",
                      "    ports: !override", "      - '127.0.0.1:18081:8080'"]
        if service == "edolams":
            lines += ["      SERVER_FORWARD_HEADERS_STRATEGY: framework", "    ports: !override []"]
        if service == "edolnotify":
            lines += ["      TELEGRAM_ENABLE: 'false'"]
    write_new(output / "compose.resume.yaml", "\n".join(lines) + "\n")
    proxy = """# Candidate for the existing Stage 9 proxy only. Logs exclude bodies/headers.
server {
    listen 8443 ssl;
    server_name _;
    ssl_certificate /etc/nginx/tls/server.crt;
    ssl_certificate_key /etc/nginx/tls/server.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log off;
    client_max_body_size 260m;
    location /api/agent/v1/ {
        proxy_pass http://edolcore:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For "";
        proxy_set_header Forwarded "";
        proxy_request_buffering off;
        proxy_read_timeout 60s;
    }
    location /api/terminal/v1/ {
        proxy_pass http://edolams:8099;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For "";
        proxy_set_header Forwarded "";
    }
    location / { return 404; }
}
"""
    write_new(output / "agent-https.resume.conf", proxy)
    print("Prepared remediation/compose.resume.yaml and agent-https.resume.conf; nothing deployed")


def ensure_audiences(admin, clients):
    for client_id, audiences in AUDIENCES.items():
        path = "/clients/" + clients[client_id]
        mappers = admin(path + "/protocol-mappers/models")
        for audience in audiences:
            if any(m.get("protocolMapper") == "oidc-audience-mapper" and
                   m.get("config", {}).get("included.custom.audience") == audience and
                   m.get("config", {}).get("access.token.claim") == "true" for m in mappers):
                continue
            admin(path + "/protocol-mappers/models", data={"name": "stage9-" + audience,
                  "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                  "config": {"included.custom.audience": audience, "access.token.claim": "true",
                             "id.token.claim": "false"}})


def identity(args, rows):
    if not args.allow_identity_update:
        raise DiagnosticError("Identity update requires --allow-identity-update; no changes made")
    env = environment(rows["keycloak"])
    user = env.get("KC_BOOTSTRAP_ADMIN_USERNAME") or env.get("KEYCLOAK_ADMIN")
    password = env.get("KC_BOOTSTRAP_ADMIN_PASSWORD") or env.get("KEYCLOAK_ADMIN_PASSWORD")
    if not user or not password:
        raise DiagnosticError("Existing Keycloak bootstrap admin unavailable; do not invent credentials")
    base = address(rows["keycloak"], 8080)
    form = urllib.parse.urlencode({"grant_type": "password", "client_id": "admin-cli",
                                  "username": user, "password": password}).encode()
    status, body = request(base + "/realms/master/protocol/openid-connect/token", data=form)
    if status != 200 or not body:
        raise DiagnosticError("Keycloak admin login failed; no identity changes made")
    token = body["access_token"]
    api = base + "/admin/realms/edol"
    def admin(path, data=None, method=None):
        code, value = request(api + path, token=token, data=data, method=method)
        if code not in [200, 201, 204]:
            raise DiagnosticError("Keycloak operation failed (HTTP " + str(code) + "); rerun safely after diagnosis")
        return value
    # Preflight all required clients/scopes before the first write.
    scopes = {s["name"]: s["id"] for s in admin(CLIENT_SCOPES_PATH)}
    clients = {}
    for client_id in AUDIENCES:
        values = admin("/clients?clientId=" + client_id)
        if len(values) != 1:
            raise DiagnosticError("Required existing service client missing: " + client_id)
        clients[client_id] = values[0]["id"]
    if AGENT_MANAGE_SCOPE not in scopes:
        admin(CLIENT_SCOPES_PATH, data={"name": AGENT_MANAGE_SCOPE, "protocol": "openid-connect",
              "attributes": {"include.in.token.scope": "true", "display.on.consent.screen": "false"}})
        scopes = {s["name"]: s["id"] for s in admin(CLIENT_SCOPES_PATH)}
    ensure_audiences(admin, clients)
    admin("/clients/" + clients["edol-hub-service"] + "/optional-client-scopes/" + scopes[AGENT_MANAGE_SCOPE], method="PUT")
    print("Existing service audience mappers and optional Agent management scope ensured; no clients/users recreated")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["check", "prepare", "identity"])
    parser.add_argument("--run-dir", type=Path, default=Path("/home/serge/edol-stage9-hardware"))
    parser.add_argument("--tenant", default="7159f3c8-1976-4c3e-ad4f-124bc1a9e44a")
    parser.add_argument("--printer", default="01a16493-e4ed-766b-8e75-53e168d39efa")
    parser.add_argument("--host", default="192.168.0.200")
    parser.add_argument("--image-tag")
    parser.add_argument("--allow-identity-update", action="store_true")
    args = parser.parse_args()
    try:
        if not (args.run_dir / "compose.backend.yaml").is_file():
            raise DiagnosticError("Existing compose.backend.yaml not found; no replacement environment created")
        rows = containers()
        if args.action == "check":
            report = check(args, rows)
            passed = ready(report)
            report["readiness"] = "READY_FOR_OWNER_ACTION" if passed else "BLOCKED"
            print(json.dumps(report, indent=2))
            # This report is connectivity/configuration evidence, not hardware acceptance.
            return 0 if passed else 1
        if args.action == "prepare":
            prepare(args, rows)
        else:
            identity(args, rows)
        return 0
    except DiagnosticError as error:
        print(str(error), file=sys.stderr)
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired):
        print("Operation stopped: unavailable dependency or invalid configuration; no raw diagnostics printed", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
