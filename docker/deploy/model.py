"""Shared Home/secure deployment model. Generated files belong outside Git."""

import copy
import base64
import ipaddress
import json
import re
from urllib.parse import urlsplit


SERVICES = {
    "edolhub": ("hub", 8090), "edolcore": ("core", 8080),
    "edolams": ("ams", 8099), "edolnotify": ("notify", None),
}
AMS_READINESS_HEADERS = {"Authorization": "EDOL-Terminal readiness-probe"}
SCOPES = {
    "hub": "tenant.context core.printer.read core.printer.manage core.state.read core.command.execute core.media.read core.agent.manage ams.terminal.manage",
    "ams": "tenant.context core.state.read hub.spool.read hub.spool.change",
    "notify": "tenant.context core.printer.read core.state.read core.command.execute core.media.read hub.notify.read",
}


def validate(config):
    """Reject ambiguous ownership, exposure and instance destinations before mutation."""
    if config.get("environment") not in ("dev", "prod") or config.get("mode") not in ("secure", "home"):
        raise ValueError("Explicit environment and mode are required")
    expected = f"edol-{config['environment']}-{config['mode']}"
    if config.get("project") != expected:
        raise ValueError("Project must match the environment/mode")
    validate_network(config)
    if config["mode"] == "secure":
        for key in ("hub_origin", "auth_origin"):
            u = urlsplit(config[key])
            if u.scheme != "https" or not u.hostname or u.path or u.query or u.fragment or u.username:
                raise ValueError("Secure origins must be exact HTTPS origins")
    validate_secrets(config)
    return config


def validate_network(config):
    ipaddress.IPv4Address(config["bind_address"])
    if not ipaddress.ip_address(config["bind_address"]).is_private:
        raise ValueError("Use a private or loopback bind address")
    network = ipaddress.IPv4Network(config["subnet"])
    if not network.is_private or network.prefixlen != 24:
        raise ValueError("A dedicated private /24 subnet is required")
    for key in ("hub_port", "core_port", "ams_port", "mqtt_port"):
        if not isinstance(config[key], int) or not 1024 <= config[key] <= 65535:
            raise ValueError("Invalid instance port")
    if len({config[k] for k in ("hub_port", "core_port", "ams_port", "mqtt_port")}) != 4:
        raise ValueError("Instance ports must be distinct")


def validate_secrets(config):
    secrets = config["secrets"]
    required = ["postgres", "home", "core_web_admin"] if config["mode"] == "home" else [
        "postgres", "core_web_admin", "keycloak_db", "keycloak_admin", "web", "hub_service", "ams_service", "notify_service",
        "core_runtime", "core_flyway", "catalog", "hub_runtime", "hub_flyway", "ams_runtime", "ams_flyway",
        "mqtt_core", "mqtt_hub", "mqtt_ams", "mqtt_notify", "mqtt_control", "mqtt_ingress", "mqtt_command",
        "agent_pairing", "agent_credential", "terminal_pairing", "terminal_credential", "telegram", "truststore",
    ]
    for key in required:
        value = secrets.get(key, "")
        if len(value) < 16 or "replace" in value.lower():
            raise ValueError(f"Protected secret is missing or still an example: {key}")
        if any(char.isspace() for char in value) or "$" in value:
            raise ValueError("Use generated secrets without whitespace or dollar signs")
    if config["mode"] == "secure":
        validate_crypto(secrets)
        ipaddress.IPv4Address(config["proxy_address"])


def validate_crypto(secrets):
    keys = [secrets[k] for k in ("agent_pairing", "agent_credential", "terminal_pairing", "terminal_credential", "telegram")]
    if any(len(base64.b64decode(key, validate=True)) != 32 for key in keys) or len(set(keys)) != len(keys):
        raise ValueError("Use independent base64-encoded 32-byte keys")
    mqtt = [secrets["mqtt_" + k] for k in ("core", "hub", "ams", "notify", "control", "ingress", "command")]
    if min(map(len, mqtt)) < 32 or len(set(mqtt)) != 7:
        raise ValueError("MQTT accounts require distinct passwords of at least 32 characters")




def smtp_settings(config):
    """Keycloak compares SMTP boolean strings case-sensitively."""
    settings = {key: str(config.get("smtp", {}).get(key, ""))
                for key in ("host", "port", "from", "auth", "user", "password", "starttls")}
    for flag in ("auth", "starttls"):
        value = settings[flag].strip().lower()
        if value not in ("true", "false"):
            raise ValueError("SMTP auth/starttls must be booleans or true/false strings")
        settings[flag] = value
    settings["fromDisplayName"] = "EDOL"
    return settings


def realm(config, source):
    """Extend the reviewed browser realm with narrowly scoped service clients."""
    c = config
    values = {
        "EDOL_KEYCLOAK_SSL_REQUIRED": "all", "EDOL_HUB_WEB_CLIENT_SECRET": c["secrets"]["web"],
        "EDOL_HUB_WEB_REDIRECT_URI": c["hub_origin"] + "/login/oauth2/code/edol-keycloak",
        "EDOL_HUB_WEB_ORIGIN": c["hub_origin"], "EDOL_HUB_WEB_POST_LOGOUT_REDIRECT_URI": c["hub_origin"] + "/",
        **{f"EDOL_SMTP_{k.upper()}": v for k, v in smtp_settings(c).items()},
    }
    for key in ("HOST", "PORT", "FROM", "AUTH", "USER", "PASSWORD", "STARTTLS"):
        values.setdefault("EDOL_SMTP_" + key, "")
    def substitute(value):
        if isinstance(value, str):
            return re.sub(r"\$\{([^}]+)}", lambda m: values[m[1]], value)
        if isinstance(value, list):
            return [substitute(v) for v in value]
        if isinstance(value, dict):
            return {k: substitute(v) for k, v in value.items()}
        return value
    result = substitute(copy.deepcopy(source))
    result["clientScopes"] = []
    for scope, claims in (("profile", [("username", "preferred_username", "String")]),
                          ("email", [("email", "email", "String"), ("emailVerified", "email_verified", "boolean")])):
        result["clientScopes"].append({
            "name": scope, "protocol": "openid-connect", "attributes": {"include.in.token.scope": "true"},
            "protocolMappers": [{"name": claim, "protocol": "openid-connect",
                                 "protocolMapper": "oidc-usermodel-property-mapper",
                                 "config": {"user.attribute": attribute, "claim.name": claim,
                                            "jsonType.label": kind, "id.token.claim": "true",
                                            "access.token.claim": "true", "userinfo.token.claim": "true"}}
                                for attribute, claim, kind in claims],
        })
    for scope in sorted(set(" ".join(SCOPES.values()).split())):
        result["clientScopes"].append({"name": scope, "protocol": "openid-connect",
                                       "attributes": {"include.in.token.scope": "true"}})
    for service, scopes in SCOPES.items():
        audiences = {"hub": ["edol-core-api", "edol-ams-api"],
                     "ams": ["edol-core-api", "edol-hub-api"],
                     "notify": ["edol-core-api", "edol-hub-api"]}[service]
        result["clients"].append({
            "clientId": f"edol-{service}-service", "protocol": "openid-connect", "enabled": True,
            "publicClient": False, "clientAuthenticatorType": "client-secret",
            "secret": c["secrets"][service + "_service"], "serviceAccountsEnabled": True,
            "standardFlowEnabled": False, "implicitFlowEnabled": False, "directAccessGrantsEnabled": False,
            "fullScopeAllowed": False, "defaultClientScopes": scopes.split(), "optionalClientScopes": [],
            "protocolMappers": [{"name": audience, "protocol": "openid-connect",
                                  "protocolMapper": "oidc-audience-mapper",
                                  "config": {"included.custom.audience": audience,
                                             "access.token.claim": "true", "id.token.claim": "false"}}
                                 for audience in audiences],
        })
    return result


def infrastructure(c, repo, state):
    s = c["secrets"]
    secure = c["mode"] == "secure"
    db_password = s["postgres"]
    services = {
        "postgres": {"image": "postgres:17", "environment": {"POSTGRES_PASSWORD": db_password},
                     "volumes": ["postgres-data:/var/lib/postgresql/data"],
                     "healthcheck": {"test": ["CMD-SHELL", "pg_isready -U postgres"],
                                     "interval": "3s", "timeout": "3s", "retries": 40}},
        "mqtt": {"image": "eclipse-mosquitto:2.0.22", "user": f"{c.get('uid', 1000)}:{c.get('gid', 1000)}",
                 "volumes": [f"{repo}/docker/mosquitto/{'secure' if secure else 'home'}.conf:/mosquitto/config/mosquitto.conf:ro"],
                 "ports": [f"{c['bind_address']}:{c['mqtt_port']}:{8883 if secure else 1883}"]},
    }
    if secure:
        services["mqtt"]["volumes"] += [f"{state}/mqtt:/mosquitto/data", f"{c['mqtt_cert_dir']}:/mosquitto/certs:ro"]
        services["keycloak"] = {
            "build": {"context": f"{repo}/docker/keycloak"}, "image": c["project"] + "-keycloak:" + c["revision"],
            "command": ["start", "--optimized", "--import-realm"],
            "environment": {"KC_DB_URL": "jdbc:postgresql://postgres:5432/edol_keycloak",
                            "KC_DB_USERNAME": "keycloak", "KC_DB_PASSWORD": s["keycloak_db"],
                            "KC_BOOTSTRAP_ADMIN_USERNAME": c["keycloak_admin_user"], "KC_BOOTSTRAP_ADMIN_PASSWORD": s["keycloak_admin"],
                            "KC_HOSTNAME": c["auth_origin"], "KC_HOSTNAME_STRICT": "true",
                            "KC_HTTP_ENABLED": "true", "KC_PROXY_HEADERS": "xforwarded",
                            "KC_PROXY_TRUSTED_ADDRESSES": c["proxy_address"]},
            "volumes": [f"{state}/edol-realm.json:/opt/keycloak/data/import/edol-realm.json:ro"],
            "networks": {"private": {}, "reverse-proxy": {"aliases": [f"edol-keycloak-{c['environment']}"]}},
        }
    subnet = ipaddress.IPv4Network(c["subnet"])
    for name, offset in (("postgres", 10), ("mqtt", 11), ("keycloak", 12)):
        if name in services:
            services[name].setdefault("networks", {})["private"] = {"ipv4_address": str(subnet[offset])}
    return services


def common_app(c, module, port):
    s = c["secrets"]
    secure = c["mode"] == "secure"
    mode = "secure-multi-tenant" if secure else "home"
    props = {
        "edol.deployment.mode": mode, "server.port": port or 8080,
        "mqttServer.url": f"{'ssl' if secure else 'tcp'}://mqtt:{8883 if secure else 1883}",
        "edol-core.url": "http://edolcore:8080", "edol-hub.url": "http://edolhub:8090",
        "edol-ams.url": "http://edolams:8099", "logging.file.name": f"/app/logs/{module}.log",
    }
    env = {"TZ": "Europe/Kyiv", "SPRING_PROFILES_ACTIVE": mode if secure else "home",
           "SERVER_PORT": str(port or 8080), "POSTGRES_DB": "edol_devices", "POSTGRES_USER": "home",
           "POSTGRES_PASSWORD": s.get("home", ""),
           "QR_URL": c["hub_origin"]}
    if module == "core":
        env.update({"WEB_ADMIN_NAME": "edol-admin", "WEB_ADMIN_PASSWORD": s["core_web_admin"]})
    return props, env


def database_app(c, module, props, env):
    s = c["secrets"]
    secure = c["mode"] == "secure"
    if module in ("core", "hub") or (module == "ams" and secure):
        database = "edol_hub" if module == "hub" else "edol_devices"
        role = module + "_runtime" if secure else "home"
        flyway = module + "_flyway" if secure else "home"
        props.update({"spring.datasource.url": f"jdbc:postgresql://postgres:5432/{database}",
                      "spring.datasource.username": role, "spring.datasource.password": s[role],
                      "spring.flyway.user": flyway, "spring.flyway.password": s[flyway],
                      "spring.flyway.init-sqls": f"SET ROLE {module}_schema_owner" if secure else "SELECT 1"})
        for name in (role, flyway):
            env[name.upper() + "_DB_USER"] = name
            env[name.upper() + "_DB_PASSWORD"] = s[name]
        if not secure:
            env.update({"HUB_RUNTIME_DB_USER": "home", "HUB_RUNTIME_DB_PASSWORD": s["home"],
                        "HUB_FLYWAY_DB_USER": "home", "HUB_FLYWAY_DB_PASSWORD": s["home"]})


def secure_core(c, props, env):
    s = c["secrets"]
    env.update({"CORE_CATALOG_DB_URL": "jdbc:postgresql://postgres:5432/edol_devices",
                "CORE_CATALOG_DB_USER": "edol_core_catalog_runtime", "CORE_CATALOG_DB_PASSWORD": s["catalog"]})
    props.update({"edol-core.security.trusted-service-client-ids": ",".join(f"edol-{n}-service" for n in SCOPES),
                  "edol-core.agent.enabled": True,
                  "edol-core.agent.pairing-keys": {"1": s["agent_pairing"]},
                  "edol-core.agent.credential-keys": {"1": s["agent_credential"]},
                  "edol-core.agent.mqtt.enabled": True,
                  "edol-core.agent.mqtt.admin-username": "core-agent-control",
                  "edol-core.agent.mqtt.admin-password": s["mqtt_control"],
                  "server.forward-headers-strategy": "native",
                  "server.tomcat.remoteip.internal-proxies": re.escape(c["proxy_address"])})
    for kind, username in (("integration", "core-events"), ("agent.mqtt.ingress", "core-agent-ingress"),
                           ("agent.mqtt.command", "core-agent-commands")):
        secret = {"integration": "core", "agent.mqtt.ingress": "ingress", "agent.mqtt.command": "command"}[kind]
        props[f"edol-core.mqtt.{kind}.username" if kind == "integration" else f"edol-core.{kind}-username"] = username
        props[f"edol-core.mqtt.{kind}.password" if kind == "integration" else f"edol-core.{kind}-password"] = s["mqtt_" + secret]


def secure_app(c, module, props, env):
    s = c["secrets"]
    env["EDOL_KEYCLOAK_ISSUER_URI"] = c["auth_origin"] + "/realms/edol"
    props["spring.security.oauth2.resourceserver.jwt.issuer-uri"] = env["EDOL_KEYCLOAK_ISSUER_URI"]
    if module == "hub":
        env["EDOL_HUB_WEB_CLIENT_SECRET"] = s["web"]
    for name in ("hub", "ams", "notify"):
        if name == module:
            env[f"EDOL_{name.upper()}_SERVICE_CLIENT_SECRET"] = s[name + "_service"]
            props[f"spring.security.oauth2.client.registration.edol-{name}-service.scope"] = SCOPES[name].split()
    if module == "core":
        secure_core(c, props, env)
    else:
        props[f"edol-{module}.mqtt.username"] = module + "-events"
        props[f"edol-{module}.mqtt.password"] = s["mqtt_" + module]
    if module == "hub":
        props.update({"edol-hub.agent.enabled": True, "edol-hub.runtime.mqtt-enabled": True,
                      "edol-hub.telegram.encryption-key": s["telegram"]})
    if module == "ams":
        env.update({"EDOL_AMS_TERMINAL_PAIRING_CODE_HMAC_KEY": s["terminal_pairing"],
                    "EDOL_AMS_TERMINAL_CREDENTIAL_HMAC_KEY": s["terminal_credential"],
                    "EDOL_AMS_MQTT_USERNAME": "ams-events", "EDOL_AMS_MQTT_PASSWORD": s["mqtt_ams"]})


def home_app(module, props):
    props["telegram.enable"] = False
    if module == "hub":
        props["spring.flyway.locations"] = ["classpath:db/migration", "classpath:db/home-migration"]
        props["server.servlet.session.cookie.secure"] = False
    if module == "ams":
        props["spring.autoconfigure.exclude"] = [
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"]


def app_container(c, module, port, env, repo, state):
    s = c["secrets"]
    secure = c["mode"] == "secure"
    subnet = ipaddress.IPv4Network(c["subnet"])
    app = {"build": {"context": str(repo), "dockerfile": f"docker/edol-{module}.Dockerfile"},
           "image": c["project"] + "-" + module + ":" + c["revision"], "environment": env,
           "volumes": [f"{state}/logs/{module}:/app/logs"],
           "networks": {"private": {"ipv4_address": str(subnet[{"core": 4, "hub": 5, "ams": 6, "notify": 7}[module]])}}}
    if secure:
        app["volumes"].append(f"{c['mqtt_truststore']}:/app/certs/mqtt.p12:ro")
        app["entrypoint"] = ["java", "-Djavax.net.ssl.trustStore=/app/certs/mqtt.p12",
                             "-Djavax.net.ssl.trustStorePassword=" + s["truststore"], "-jar", "/app/app.jar"]
    if module == "core":
        # Java's private staging files retain their owner after atomic promotion.
        # The same host operator must be able to archive models and metadata.
        app["user"] = f"{c.get('uid', 1000)}:{c.get('gid', 1000)}"
        app["volumes"] += [f"{state}/models:/app/models", f"{state}/snapshots:/app/camera_snapshots"]
    if module == "hub":
        if secure:
            app["networks"]["reverse-proxy"] = {"aliases": [f"edol-hub-{c['environment']}"]}
        app["ports"] = [f"{c['bind_address']}:{c['hub_port']}:8090"]
    if module in ("core", "ams"):
        app["ports"] = [f"{c['bind_address']}:{c[module + '_port']}:{port}"]
        if secure:
            app["networks"]["reverse-proxy"] = {"aliases": [f"edol-{module}-{c['environment']}"]}
    return app


def http_healthcheck(port, path, statuses, headers=None):
    extra_headers = "".join(f"{name}: {value}\\r\\n" for name, value in (headers or {}).items())
    script = (f"exec 3<>/dev/tcp/127.0.0.1/{port}; "
              f"printf 'GET {path} HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n{extra_headers}\\r\\n' >&3; "
              "IFS=' ' read -r version status rest <&3; "
              f"case \"$status\" in {'|'.join(map(str, statuses))}) exit 0;; *) exit 1;; esac")
    return {"test": ["CMD", "bash", "-ec", script], "interval": "10s", "timeout": "5s",
            "start_period": "60s", "retries": 12}


def add_healthchecks(services, config, state):
    secure = config["mode"] == "secure"
    services["edolhub"]["healthcheck"] = http_healthcheck(8090, "/login" if secure else "/", (200, 302))
    services["edolcore"]["healthcheck"] = http_healthcheck(8080, "/api/printers", (401,) if secure else (200,))
    services["edolams"]["healthcheck"] = http_healthcheck(8099, "/api/terminal/v1/state" if secure else "/ams/state",
                                                         (401,) if secure else (400,), AMS_READINESS_HEADERS if secure else None)
    services["edolnotify"]["healthcheck"] = {
        "test": ["CMD-SHELL", "test \"$(cat /proc/1/comm)\" = java && kill -0 1"],
        "interval": "10s", "timeout": "3s", "start_period": "60s", "retries": 3}
    services["edolnotify"]["labels"]["dev.edol.healthcheck"] = "process-liveness-only"
    mqtt = services["mqtt"]
    if secure:
        services["keycloak"]["healthcheck"] = http_healthcheck(9000, "/health/ready", (200,))
        mqtt["volumes"].append(f"{state}/mqtt-health.conf:/mosquitto/health/mosquitto_sub:ro")
        command = ["CMD", "env", "XDG_CONFIG_HOME=/mosquitto/health", "mosquitto_sub", "-E", "-W", "3"]
    else:
        command = ["CMD", "mosquitto_sub", "-h", "127.0.0.1", "-t", "edolcore/#", "-E", "-W", "3"]
    mqtt["healthcheck"] = {"test": command, "interval": "10s", "timeout": "5s", "start_period": "15s", "retries": 6}


def compose(config, repo, state):
    """Return a Compose model; JSON is supported directly by Docker Compose."""
    c = config
    secure = c["mode"] == "secure"
    services = infrastructure(c, repo, state)
    for service, (module, port) in SERVICES.items():
        props, env = common_app(c, module, port)
        database_app(c, module, props, env)
        if secure:
            secure_app(c, module, props, env)
        else:
            home_app(module, props)
        env["SPRING_APPLICATION_JSON"] = json.dumps(props)
        services[service] = app_container(c, module, port, env, repo, state)
    for service in services.values():
        service.setdefault("networks", {"private": {}})
        service["restart"] = "unless-stopped"
        service["labels"] = {"dev.edol.environment": c["environment"], "dev.edol.mode": c["mode"]}
    networks = {"private": {"ipam": {"config": [{"subnet": c["subnet"]}]}}}
    if secure:
        networks["reverse-proxy"] = {"external": True, "name": "edol-reverse-proxy"}
    add_healthchecks(services, c, state)
    return {"name": c["project"], "services": services, "volumes": {"postgres-data": {}}, "networks": networks}
