-- Run once against a new disposable PostgreSQL database as its deployment administrator.
-- This script is intentionally fail-closed and is not a development/production procedure.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN ('ams_schema_owner', 'ams_flyway', 'ams_runtime', 'ams_terminal_authenticator')
    ) THEN
        RAISE EXCEPTION 'Stage 8 AMS roles already exist; inspect the current database instead of rerunning this bootstrap';
    END IF;

    CREATE ROLE ams_schema_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE ams_flyway LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE ams_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE ams_terminal_authenticator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT BYPASSRLS;
END;
$$;

GRANT ams_schema_owner TO ams_flyway;
GRANT ams_terminal_authenticator TO ams_schema_owner;
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO ams_flyway, ams_runtime', current_database());
    EXECUTE format('GRANT CREATE ON DATABASE %I TO ams_schema_owner', current_database());
END;
$$;

CREATE SCHEMA ams AUTHORIZATION ams_schema_owner;
REVOKE ALL ON SCHEMA ams FROM PUBLIC;
GRANT USAGE ON SCHEMA ams TO ams_flyway, ams_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE ams_schema_owner IN SCHEMA ams
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ams_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ams_schema_owner IN SCHEMA ams
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

COMMIT;
