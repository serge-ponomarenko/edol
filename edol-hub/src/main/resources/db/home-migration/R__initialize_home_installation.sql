-- This repeatable migration is loaded only by the explicit home composition.
-- It is never part of the secure-multi-tenant Flyway locations.
--
-- The Stage 3 policies remain in the immutable versioned migration history,
-- but home is an independent single-tenant database profile. Disabling RLS is
-- transactional here: ambiguous ownership aborts without changing the schema.

ALTER TABLE hub.tenants DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.printers DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.vendors DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.material_types DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.filaments DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_group DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_item DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.job_spool_usage DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.filament_spools DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_jobs DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.printer_stats DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.maintenance_definition DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.maintenance_execution DISABLE ROW LEVEL SECURITY;
ALTER TABLE hub.print_allocation_preview DISABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS hub.home_installations
(
    singleton  boolean                   NOT NULL DEFAULT true,
    tenant_id  uuid                      NOT NULL,
    created_at timestamp with time zone  NOT NULL DEFAULT current_timestamp,

    CONSTRAINT pk_home_installations PRIMARY KEY (singleton),
    CONSTRAINT chk_home_installations_singleton CHECK (singleton),
    CONSTRAINT uk_home_installations_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_home_installations_tenant
        FOREIGN KEY (tenant_id) REFERENCES hub.tenants (id)
);

DO
$$
DECLARE
    tenant_count          integer;
    selected_tenant_id    uuid;
    installation_tenant_id uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM hub.users)
        OR EXISTS (SELECT 1 FROM hub.tenant_memberships) THEN
        RAISE EXCEPTION 'Cannot enter home mode: Hub users or memberships exist';
    END IF;

    SELECT count(*) INTO tenant_count FROM hub.tenants;
    IF tenant_count > 1 THEN
        RAISE EXCEPTION 'Cannot enter home mode: more than one Hub tenant exists';
    END IF;

    IF tenant_count = 0 THEN
        INSERT INTO hub.tenants (id, name, is_default)
        VALUES (gen_random_uuid(), 'Home installation', true)
        RETURNING id INTO selected_tenant_id;
    ELSE
        SELECT id INTO selected_tenant_id FROM hub.tenants;
    END IF;

    SELECT tenant_id
    INTO installation_tenant_id
    FROM hub.home_installations
    WHERE singleton;

    IF installation_tenant_id IS NULL THEN
        INSERT INTO hub.home_installations (singleton, tenant_id)
        VALUES (true, selected_tenant_id);
    ELSIF installation_tenant_id IS DISTINCT FROM selected_tenant_id THEN
        RAISE EXCEPTION 'Cannot enter home mode: installation tenant does not match the only Hub tenant';
    END IF;
END;
$$;
