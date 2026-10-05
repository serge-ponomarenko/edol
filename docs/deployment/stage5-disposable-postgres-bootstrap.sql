-- EDOL Stage 5 disposable Core and Hub PostgreSQL bootstrap
--
-- Run only against a fresh, isolated disposable PostgreSQL database as its
-- database administrator before either service runs Flyway. It is deliberately
-- not a Flyway migration and must never target the source development database,
-- a shared database, or production.
--
-- Invoke psql with -v stage5_smoke_database=<exact-disposable-database-name>.
-- The script refuses a database-name mismatch and refuses any pre-existing role
-- so a reused cluster cannot silently receive changed global PostgreSQL roles.

\if :{?stage5_smoke_database}
\else
  \echo 'stage5_smoke_database is required'
  \quit
\endif

SELECT current_database() = :'stage5_smoke_database' AS stage5_smoke_database_matches \gset
\if :stage5_smoke_database_matches
\else
  \echo 'Connected database does not match stage5_smoke_database'
  \quit
\endif

BEGIN;

-- Hub V2 is immutable Flyway history and requires pgcrypto. Extensions are
-- database infrastructure, so the administrator provisions this prerequisite
-- before restricted Flyway credentials are used.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN (
            'core_schema_owner',
            'core_flyway',
            'core_runtime',
            'edol_core_catalog_runtime',
            'hub_schema_owner',
            'hub_flyway',
            'hub_runtime'
        )
    ) THEN
        RAISE EXCEPTION
            'Stage 5 disposable roles already exist; use a fresh isolated PostgreSQL cluster';
    END IF;

    CREATE ROLE core_schema_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE core_flyway LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE core_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE edol_core_catalog_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE hub_schema_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE hub_flyway LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE hub_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
END;
$$;

GRANT core_schema_owner TO core_flyway;
GRANT hub_schema_owner TO hub_flyway;
REVOKE CREATE ON DATABASE :"stage5_smoke_database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"stage5_smoke_database" TO
    core_flyway,
    core_runtime,
    edol_core_catalog_runtime,
    hub_flyway,
    hub_runtime;

CREATE SCHEMA core AUTHORIZATION core_schema_owner;
CREATE SCHEMA hub AUTHORIZATION hub_schema_owner;

REVOKE ALL ON SCHEMA core FROM PUBLIC;
REVOKE ALL ON SCHEMA hub FROM PUBLIC;
GRANT USAGE ON SCHEMA core TO core_flyway, core_runtime, edol_core_catalog_runtime;
GRANT USAGE ON SCHEMA hub TO hub_flyway, hub_runtime;

COMMIT;

-- Set distinct LOGIN-role passwords only through the approved ephemeral secret
-- injection mechanism after this script succeeds. Do not add passwords here,
-- to repository environment files, or to command history.
