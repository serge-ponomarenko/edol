CREATE FUNCTION hub.current_tenant_id()
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

ALTER TABLE hub.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenants_tenant_isolation ON hub.tenants
    USING (id = hub.current_tenant_id())
    WITH CHECK (id = hub.current_tenant_id());

ALTER TABLE hub.printers ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.printers FORCE ROW LEVEL SECURITY;
CREATE POLICY printers_tenant_isolation ON hub.printers
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.vendors ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.vendors FORCE ROW LEVEL SECURITY;
CREATE POLICY vendors_tenant_isolation ON hub.vendors
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.material_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.material_types FORCE ROW LEVEL SECURITY;
CREATE POLICY material_types_tenant_isolation ON hub.material_types
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.filaments ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.filaments FORCE ROW LEVEL SECURITY;
CREATE POLICY filaments_tenant_isolation ON hub.filaments
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.print_allocation_group ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_group FORCE ROW LEVEL SECURITY;
CREATE POLICY print_allocation_group_tenant_isolation ON hub.print_allocation_group
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.print_allocation_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_item FORCE ROW LEVEL SECURITY;
CREATE POLICY print_allocation_item_tenant_isolation ON hub.print_allocation_item
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.job_spool_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.job_spool_usage FORCE ROW LEVEL SECURITY;
CREATE POLICY job_spool_usage_tenant_isolation ON hub.job_spool_usage
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

ALTER TABLE hub.filament_spools ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.filament_spools FORCE ROW LEVEL SECURITY;
CREATE POLICY filament_spools_tenant_isolation ON hub.filament_spools
    USING (EXISTS (
        SELECT 1 FROM hub.filaments filament
        WHERE filament.id = filament_spools.filament_id
          AND filament.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM hub.filaments filament
        WHERE filament.id = filament_spools.filament_id
          AND filament.tenant_id = hub.current_tenant_id()
    ));

ALTER TABLE hub.print_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY print_jobs_tenant_isolation ON hub.print_jobs
    USING (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = print_jobs.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = print_jobs.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ));

ALTER TABLE hub.printer_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.printer_stats FORCE ROW LEVEL SECURITY;
CREATE POLICY printer_stats_tenant_isolation ON hub.printer_stats
    USING (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = printer_stats.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = printer_stats.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ));

ALTER TABLE hub.maintenance_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.maintenance_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY maintenance_definition_tenant_isolation ON hub.maintenance_definition
    USING (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = maintenance_definition.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM hub.printers printer
        WHERE printer.id = maintenance_definition.printer_id
          AND printer.tenant_id = hub.current_tenant_id()
    ));

ALTER TABLE hub.maintenance_execution ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.maintenance_execution FORCE ROW LEVEL SECURITY;
CREATE POLICY maintenance_execution_tenant_isolation ON hub.maintenance_execution
    USING (EXISTS (
        SELECT 1
        FROM hub.maintenance_definition definition
        JOIN hub.printers printer ON printer.id = definition.printer_id
        WHERE definition.id = maintenance_execution.maintenance_id
          AND printer.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1
        FROM hub.maintenance_definition definition
        JOIN hub.printers printer ON printer.id = definition.printer_id
        WHERE definition.id = maintenance_execution.maintenance_id
          AND printer.tenant_id = hub.current_tenant_id()
    ));

ALTER TABLE hub.print_allocation_preview ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_preview FORCE ROW LEVEL SECURITY;
CREATE POLICY print_allocation_preview_tenant_isolation ON hub.print_allocation_preview
    USING (EXISTS (
        SELECT 1
        FROM hub.print_jobs job
        JOIN hub.printers printer ON printer.id = job.printer_id
        WHERE job.id = print_allocation_preview.print_job_id
          AND printer.tenant_id = hub.current_tenant_id()
    ))
    WITH CHECK (EXISTS (
        SELECT 1
        FROM hub.print_jobs job
        JOIN hub.printers printer ON printer.id = job.printer_id
        WHERE job.id = print_allocation_preview.print_job_id
          AND printer.tenant_id = hub.current_tenant_id()
    ));

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_runtime') THEN
        GRANT USAGE ON SCHEMA hub TO hub_runtime;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA hub TO hub_runtime;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA hub TO hub_runtime;
        GRANT EXECUTE ON FUNCTION hub.current_tenant_id() TO hub_runtime;
        ALTER DEFAULT PRIVILEGES IN SCHEMA hub
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hub_runtime;
        ALTER DEFAULT PRIVILEGES IN SCHEMA hub
            GRANT USAGE, SELECT ON SEQUENCES TO hub_runtime;
    END IF;
END;
$$;
