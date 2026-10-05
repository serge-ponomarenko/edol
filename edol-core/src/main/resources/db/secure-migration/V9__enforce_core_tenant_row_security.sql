DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM core.printers WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot enforce Core tenant ownership: printer tenant_id is missing';
    END IF;
END;
$$;

ALTER TABLE core.printers
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE TABLE core.printer_provisioning_requests
(
    idempotency_key UUID PRIMARY KEY,
    tenant_id      UUID      NOT NULL,
    printer_id     UUID      NOT NULL UNIQUE,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT fk_printer_provisioning_request_printer
        FOREIGN KEY (printer_id)
            REFERENCES core.printers (id)
);

CREATE FUNCTION core.current_tenant_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS
$$
    SELECT CASE
        WHEN current_setting('edol.tenant_id', true)
             ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            THEN current_setting('edol.tenant_id', true)::uuid
        ELSE NULL
    END
$$;

ALTER TABLE core.printers ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.printers FORCE ROW LEVEL SECURITY;
CREATE POLICY printers_tenant_isolation ON core.printers
    USING (tenant_id = core.current_tenant_id())
    WITH CHECK (tenant_id = core.current_tenant_id());

ALTER TABLE core.printer_connection_configurations ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.printer_connection_configurations FORCE ROW LEVEL SECURITY;
CREATE POLICY printer_connection_configurations_tenant_isolation
    ON core.printer_connection_configurations
    USING (EXISTS (
        SELECT 1
        FROM core.printers printer
        WHERE printer.id = printer_connection_configurations.printer_id
          AND printer.tenant_id = core.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1
        FROM core.printers printer
        WHERE printer.id = printer_connection_configurations.printer_id
          AND printer.tenant_id = core.current_tenant_id()
    ));

ALTER TABLE core.active_print_context ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.active_print_context FORCE ROW LEVEL SECURITY;
CREATE POLICY active_print_context_tenant_isolation ON core.active_print_context
    USING (EXISTS (
        SELECT 1
        FROM core.printers printer
        WHERE printer.id = active_print_context.printer_id
          AND printer.tenant_id = core.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1
        FROM core.printers printer
        WHERE printer.id = active_print_context.printer_id
          AND printer.tenant_id = core.current_tenant_id()
    ));

ALTER TABLE core.printer_provisioning_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.printer_provisioning_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY printer_provisioning_requests_tenant_isolation
    ON core.printer_provisioning_requests
    USING (tenant_id = core.current_tenant_id())
    WITH CHECK (tenant_id = core.current_tenant_id());

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_runtime') THEN
        GRANT USAGE ON SCHEMA core TO core_runtime;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO core_runtime;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA core TO core_runtime;
        GRANT EXECUTE ON FUNCTION core.current_tenant_id() TO core_runtime;
        ALTER DEFAULT PRIVILEGES IN SCHEMA core
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_runtime;
        ALTER DEFAULT PRIVILEGES IN SCHEMA core
            GRANT USAGE, SELECT ON SEQUENCES TO core_runtime;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'edol_core_catalog_runtime') THEN
        GRANT USAGE ON SCHEMA core TO edol_core_catalog_runtime;
        GRANT SELECT (id, tenant_id, enabled) ON core.printers TO edol_core_catalog_runtime;
        GRANT EXECUTE ON FUNCTION core.current_tenant_id() TO edol_core_catalog_runtime;
        CREATE POLICY core_runtime_catalog_read ON core.printers
            FOR SELECT TO edol_core_catalog_runtime
            USING (true);
    END IF;
END;
$$;
