"""Create missing deployment roles/databases; never populate application data."""



def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def provision(config):
    """Create missing dedicated roles/databases without resetting existing state."""
    statements = role_statements(config)
    databases = ["edol_devices", "edol_hub"] + (["edol_keycloak"] if config["mode"] == "secure" else [])
    for database in databases:
        owner = "home" if config["mode"] == "home" else "postgres"
        if database == "edol_keycloak":
            owner = "keycloak"
        statements += [f"SELECT 'CREATE DATABASE {database} OWNER {owner}' WHERE NOT EXISTS "
                       f"(SELECT FROM pg_database WHERE datname='{database}')\n\\gexec",
                       f"REVOKE ALL ON DATABASE {database} FROM PUBLIC;"]
    statements += schema_statements(config)
    return "\n".join(statements) + "\n"


def role_statements(config):
    s = config["secrets"]
    roles = {"home": s["home"]} if config["mode"] == "home" else {
        **{k: s[k] for k in ("core_runtime", "core_flyway", "hub_runtime", "hub_flyway", "ams_runtime", "ams_flyway")},
        "keycloak": s["keycloak_db"], "edol_core_catalog_runtime": s["catalog"],
    }
    statements = ["SET standard_conforming_strings = on;"]
    for role, password in roles.items():
        query = f"CREATE ROLE {role} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD {literal(password)}"
        statements.append(f"SELECT {literal(query)} WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname={literal(role)})\n\\gexec")
    if config["mode"] == "secure":
        for module in ("core", "hub", "ams"):
            role = module + "_schema_owner"
            query = f"CREATE ROLE {role} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS"
            statements += [f"SELECT {literal(query)} WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname={literal(role)})\n\\gexec",
                           f"GRANT {role} TO {module}_flyway;"]
        for role, bypass, parent in (("core_agent_authenticator", "NOBYPASSRLS", "core"),
                                     ("ams_terminal_authenticator", "BYPASSRLS", "ams")):
            query = f"CREATE ROLE {role} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT {bypass}"
            statements += [f"SELECT {literal(query)} WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname={literal(role)})\n\\gexec",
                           f"GRANT {role} TO {parent}_schema_owner;"]
    return statements


def schema_statements(config):
    statements = []
    for module in ("core", "hub", "ams"):
        if module == "ams" and config["mode"] == "home":
            continue
        database = "edol_hub" if module == "hub" else "edol_devices"
        owner = module + "_schema_owner" if config["mode"] == "secure" else "home"
        statements += [f"\\connect {database}", "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
                       f"CREATE SCHEMA IF NOT EXISTS {module} AUTHORIZATION {owner};",
                       f"REVOKE ALL ON SCHEMA {module} FROM PUBLIC;",
                       "REVOKE CREATE ON SCHEMA public FROM PUBLIC;"]
        if config["mode"] == "secure":
            statements += [f"GRANT CONNECT ON DATABASE {database} TO {module}_runtime, {module}_flyway;",
                           f"GRANT CREATE ON DATABASE {database} TO {owner};",
                           f"GRANT USAGE ON SCHEMA {module} TO {module}_runtime, {module}_flyway;",
                           f"ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA {module} "
                           f"GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {module}_runtime;",
                           f"ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA {module} "
                           f"GRANT USAGE, SELECT ON SEQUENCES TO {module}_runtime;"]
    if config["mode"] == "secure":
        statements += ["\\connect edol_devices", "GRANT CONNECT ON DATABASE edol_devices TO edol_core_catalog_runtime;"]
    return statements


def post_migration(config):
    if config["mode"] == "home":
        return "SELECT 1;\n"
    statements = []
    for module in ("core", "hub", "ams"):
        database = "edol_hub" if module == "hub" else "edol_devices"
        statements += [f"\\connect {database}",
                       f"GRANT SELECT, INSERT, UPDATE, DELETE ON {module}.flyway_schema_history TO {module}_flyway;",
                       f"REVOKE ALL ON {module}.flyway_schema_history FROM {module}_runtime;"]
    return "\n".join(statements) + "\n"


