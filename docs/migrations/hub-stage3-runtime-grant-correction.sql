-- EDOL Hub Stage 3 runtime-grant correction
--
-- Execute once per PostgreSQL database as the database deployment administrator
-- after Hub Flyway has created hub.flyway_schema_history. This is deliberately
-- separate from Flyway: it corrects deployment role grants and never changes
-- schema, application data, or Flyway history contents.
--
-- The Stage 3 V7 migration and the original bootstrap grant runtime CRUD on all
-- Hub tables. Flyway history is not application data and must be writable only
-- by the Flyway executor through its schema-owner membership.

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_runtime') THEN
        RAISE EXCEPTION 'hub_runtime does not exist; run the Stage 3 role bootstrap first';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_flyway') THEN
        RAISE EXCEPTION 'hub_flyway does not exist; run the Stage 3 role bootstrap first';
    END IF;

    IF to_regclass('hub.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'hub.flyway_schema_history does not exist; run Hub Flyway before this correction';
    END IF;

    REVOKE ALL PRIVILEGES ON TABLE hub.flyway_schema_history FROM hub_runtime;
END;
$$;
