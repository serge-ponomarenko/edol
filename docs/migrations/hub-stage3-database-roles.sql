-- EDOL Hub Stage 3 database-role bootstrap
--
-- Execute exactly once per PostgreSQL database as a database administrator.
-- This is deliberately separate from Hub Flyway migrations: PostgreSQL roles and
-- object ownership are deployment administration, whereas Flyway owns Hub schema
-- and data evolution. The script is transactional and fails before changing
-- anything when a Stage 3 role already exists.
--
-- It creates the three-stage role model, transfers ownership of Hub-owned
-- relations and non-extension functions, and installs least-privilege grants.
-- It never changes table data, does not touch the core schema, and leaves
-- extension-owned functions (for example pgcrypto) with their extension owner.
--
-- Set passwords for hub_flyway and hub_runtime through the approved secret
-- mechanism after this script succeeds. Do not add passwords to this file.

DO
$$
DECLARE
    relation_record RECORD;
    function_record RECORD;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN ('hub_schema_owner', 'hub_flyway', 'hub_runtime')
    ) THEN
        RAISE EXCEPTION
            'Stage 3 Hub roles already exist; inspect the current state before rerunning this bootstrap';
    END IF;

    CREATE ROLE hub_schema_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE hub_flyway LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    CREATE ROLE hub_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;

    GRANT hub_schema_owner TO hub_flyway;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO hub_flyway, hub_runtime', current_database());
    CREATE SCHEMA IF NOT EXISTS hub AUTHORIZATION hub_schema_owner;
    -- Flyway reads its history before applying init SQL that sets the owner role.
    GRANT USAGE ON SCHEMA hub TO hub_flyway;

    FOR relation_record IN
        SELECT CASE c.relkind
                   WHEN 'r' THEN 'TABLE'
                   WHEN 'p' THEN 'TABLE'
                   WHEN 'S' THEN 'SEQUENCE'
                   WHEN 'v' THEN 'VIEW'
                   WHEN 'm' THEN 'MATERIALIZED VIEW'
               END AS object_type,
               n.nspname,
               c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'hub'
          AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
          AND (
              c.relkind <> 'S'
              OR NOT EXISTS (
                  -- A table-owned identity or serial sequence changes owner
                  -- automatically when its owning table changes owner.
                  SELECT 1
                  FROM pg_depend dependency
                  WHERE dependency.classid = 'pg_class'::regclass
                    AND dependency.objid = c.oid
                    AND dependency.deptype IN ('a', 'i')
              )
          )
        ORDER BY c.relkind, c.relname
    LOOP
        EXECUTE format(
            'ALTER %s %I.%I OWNER TO hub_schema_owner',
            relation_record.object_type,
            relation_record.nspname,
            relation_record.relname
        );
    END LOOP;

    FOR function_record IN
        SELECT n.nspname,
               p.proname,
               pg_get_function_identity_arguments(p.oid) AS identity_arguments
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'hub'
          AND NOT EXISTS (
              SELECT 1
              FROM pg_depend dependency
              WHERE dependency.classid = 'pg_proc'::regclass
                AND dependency.objid = p.oid
                AND dependency.deptype = 'e'
          )
        ORDER BY p.proname, pg_get_function_identity_arguments(p.oid)
    LOOP
        EXECUTE format(
            'ALTER FUNCTION %I.%I(%s) OWNER TO hub_schema_owner',
            function_record.nspname,
            function_record.proname,
            function_record.identity_arguments
        );
    END LOOP;

    ALTER SCHEMA hub OWNER TO hub_schema_owner;

    REVOKE ALL ON SCHEMA hub FROM PUBLIC;
    REVOKE ALL ON ALL TABLES IN SCHEMA hub FROM PUBLIC;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA hub FROM PUBLIC;
    REVOKE ALL ON ALL FUNCTIONS IN SCHEMA hub FROM PUBLIC;

    IF to_regclass('hub.flyway_schema_history') IS NOT NULL THEN
        EXECUTE
            'GRANT SELECT, INSERT, UPDATE, DELETE '
            || 'ON TABLE hub.flyway_schema_history TO hub_flyway';
    END IF;

    GRANT USAGE ON SCHEMA hub TO hub_runtime;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA hub TO hub_runtime;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA hub TO hub_runtime;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA hub TO hub_runtime;

    ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hub_runtime;
    ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
        GRANT USAGE, SELECT ON SEQUENCES TO hub_runtime;
    ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
        REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
    ALTER DEFAULT PRIVILEGES FOR ROLE hub_schema_owner IN SCHEMA hub
        GRANT EXECUTE ON FUNCTIONS TO hub_runtime;
END;
$$;
