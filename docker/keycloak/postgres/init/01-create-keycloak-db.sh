#!/usr/bin/env bash
set -euo pipefail

: "${KEYCLOAK_DB_NAME:?KEYCLOAK_DB_NAME is required}"
: "${KEYCLOAK_DB_USER:?KEYCLOAK_DB_USER is required}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=keycloak_db_name="$KEYCLOAK_DB_NAME" \
  --set=keycloak_db_user="$KEYCLOAK_DB_USER" \
  --set=keycloak_db_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L',
    :'keycloak_db_user',
    :'keycloak_db_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'keycloak_db_user'
)
\gexec

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
SQL

psql --username "$POSTGRES_USER" --dbname "$KEYCLOAK_DB_NAME" <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
