-- Run as a PostgreSQL cluster administrator against the maintenance database.
-- This is controlled secure-multi-tenant infrastructure bootstrap, never a Hub
-- Flyway migration. Do not run it for home mode; home does not own a Keycloak
-- database, role, secret, or container.
--
-- Example:
--   psql -v ON_ERROR_STOP=1 \
--     -v keycloak_db_name=edol_keycloak \
--     -v keycloak_db_user=edol_keycloak \
--     -v edol_database=<existing_edol_database> \
--     -v hub_runtime_db_user=hub_runtime \
--     -f docs/deployment/keycloak-postgres-bootstrap.sql postgres
--
-- The script deliberately prompts for the role password. Do not put a password
-- on a command line, in this file, in shell history, or in source control.

\if :{?keycloak_db_name}
\else
  \echo 'keycloak_db_name is required'
  \quit
\endif

\if :{?keycloak_db_user}
\else
  \echo 'keycloak_db_user is required'
  \quit
\endif

\if :{?edol_database}
\else
  \echo 'edol_database is required for the Hub/Core negative-access check'
  \quit
\endif

\if :{?hub_runtime_db_user}
\else
  \echo 'hub_runtime_db_user is required for the Keycloak database negative-access check'
  \quit
\endif

SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'keycloak_db_user'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'keycloak_db_user'
)
\gexec

SELECT format(
    'ALTER ROLE %I NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'keycloak_db_user'
)
\gexec

\password :keycloak_db_user

SELECT format(
    'CREATE DATABASE %I OWNER %I TEMPLATE template0 ENCODING ''UTF8''',
    :'keycloak_db_name',
    :'keycloak_db_user'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = :'keycloak_db_name'
)
\gexec

SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'keycloak_db_name')
\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'keycloak_db_name', :'keycloak_db_user')
\gexec

SELECT format('REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I', :'keycloak_db_name', :'hub_runtime_db_user')
\gexec

\connect :edol_database

-- Do not revoke PUBLIC CONNECT here: that can affect an existing deployment.
-- Instead fail closed and require a separately approved DBA hardening change if
-- the existing EDOL database grants CONNECT to PUBLIC.
SELECT CASE
    WHEN has_database_privilege(:'keycloak_db_user', current_database(), 'CONNECT')
        THEN 'true'
    WHEN has_schema_privilege(:'keycloak_db_user', 'hub', 'USAGE')
        THEN 'true'
    WHEN has_schema_privilege(:'keycloak_db_user', 'core', 'USAGE')
        THEN 'true'
    ELSE 'false'
END AS isolation_failed
\gset

\if :isolation_failed
  \echo 'Keycloak role retains EDOL database or schema access; bootstrap stopped.'
  \quit
\endif
