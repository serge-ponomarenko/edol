CREATE TABLE hub.users
(
    id           uuid                     NOT NULL,
    issuer       varchar(2048)            NOT NULL,
    subject      varchar(255)             NOT NULL,
    display_name varchar(255),
    email        varchar(320),
    created_at   timestamp with time zone NOT NULL DEFAULT current_timestamp,
    updated_at   timestamp with time zone NOT NULL DEFAULT current_timestamp,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_issuer_subject UNIQUE (issuer, subject)
);

CREATE TABLE hub.tenant_memberships
(
    id         uuid                     NOT NULL,
    tenant_id  uuid                     NOT NULL,
    user_id    uuid                     NOT NULL,
    role       varchar(32)              NOT NULL,
    status     varchar(32)              NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    updated_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    revoked_at timestamp with time zone,

    CONSTRAINT pk_tenant_memberships PRIMARY KEY (id),
    CONSTRAINT fk_tenant_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES hub.tenants (id),
    CONSTRAINT fk_tenant_memberships_user FOREIGN KEY (user_id) REFERENCES hub.users (id),
    CONSTRAINT uk_tenant_memberships_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_tenant_memberships_role CHECK (role IN ('OWNER')),
    CONSTRAINT chk_tenant_memberships_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_tenant_memberships_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_tenant_memberships_user ON hub.tenant_memberships (user_id);
CREATE INDEX idx_tenant_memberships_tenant_status ON hub.tenant_memberships (tenant_id, status);

CREATE FUNCTION hub.reject_user_identity_change()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.issuer IS DISTINCT FROM OLD.issuer OR NEW.subject IS DISTINCT FROM OLD.subject THEN
        RAISE EXCEPTION 'Hub user OIDC issuer and subject are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_reject_identity_change
    BEFORE UPDATE OF issuer, subject ON hub.users
    FOR EACH ROW
EXECUTE FUNCTION hub.reject_user_identity_change();

ALTER TABLE hub.printers
    ADD CONSTRAINT uk_printers_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE hub.vendors
    ADD CONSTRAINT uk_vendors_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE hub.material_types
    ADD CONSTRAINT uk_material_types_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE hub.filaments
    ADD CONSTRAINT uk_filaments_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE hub.print_allocation_group
    ADD COLUMN tenant_id uuid;

UPDATE hub.print_allocation_group allocation_group
SET tenant_id = printer.tenant_id
FROM hub.print_allocation_preview preview
         JOIN hub.print_jobs print_job ON print_job.id = preview.print_job_id
         JOIN hub.printers printer ON printer.id = print_job.printer_id
WHERE allocation_group.preview_id = preview.id;

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM hub.print_allocation_group WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot backfill print allocation group tenant ownership: tenant backfill is incomplete';
    END IF;
END;
$$;

ALTER TABLE hub.print_allocation_group
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE hub.print_allocation_group
    ADD CONSTRAINT fk_print_allocation_group_tenant
        FOREIGN KEY (tenant_id) REFERENCES hub.tenants (id),
    ADD CONSTRAINT uk_print_allocation_group_tenant_id UNIQUE (tenant_id, id),
    ADD CONSTRAINT fk_print_allocation_group_filament_tenant
        FOREIGN KEY (tenant_id, filament_id)
            REFERENCES hub.filaments (tenant_id, id);

CREATE INDEX idx_print_allocation_group_tenant ON hub.print_allocation_group (tenant_id);
CREATE INDEX idx_print_allocation_group_tenant_preview
    ON hub.print_allocation_group (tenant_id, preview_id);

ALTER TABLE hub.print_allocation_item
    ADD COLUMN tenant_id uuid;

UPDATE hub.print_allocation_item allocation_item
SET tenant_id = allocation_group.tenant_id
FROM hub.print_allocation_group allocation_group
WHERE allocation_item.group_id = allocation_group.id;

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM hub.print_allocation_item WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot backfill print allocation item tenant ownership: tenant backfill is incomplete';
    END IF;
END;
$$;

ALTER TABLE hub.print_allocation_item
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE hub.print_allocation_item
    ADD CONSTRAINT fk_print_allocation_item_tenant
        FOREIGN KEY (tenant_id) REFERENCES hub.tenants (id),
    ADD CONSTRAINT fk_print_allocation_item_group_tenant
        FOREIGN KEY (tenant_id, group_id)
            REFERENCES hub.print_allocation_group (tenant_id, id);

CREATE INDEX idx_print_allocation_item_tenant ON hub.print_allocation_item (tenant_id);
CREATE INDEX idx_print_allocation_item_tenant_group
    ON hub.print_allocation_item (tenant_id, group_id);
CREATE INDEX idx_print_allocation_item_tenant_spool
    ON hub.print_allocation_item (tenant_id, filament_spool_id);

ALTER TABLE hub.job_spool_usage
    ADD COLUMN tenant_id uuid;

UPDATE hub.job_spool_usage usage
SET tenant_id = printer.tenant_id
FROM hub.print_jobs print_job
         JOIN hub.printers printer ON printer.id = print_job.printer_id
WHERE usage.print_job_id = print_job.id;

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM hub.job_spool_usage WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot backfill job spool usage tenant ownership: tenant backfill is incomplete';
    END IF;
END;
$$;

ALTER TABLE hub.job_spool_usage
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE hub.job_spool_usage
    ADD CONSTRAINT fk_job_spool_usage_tenant
        FOREIGN KEY (tenant_id) REFERENCES hub.tenants (id);

CREATE INDEX idx_job_spool_usage_tenant ON hub.job_spool_usage (tenant_id);
CREATE INDEX idx_job_spool_usage_tenant_print_job
    ON hub.job_spool_usage (tenant_id, print_job_id);
CREATE INDEX idx_job_spool_usage_tenant_filament_spool
    ON hub.job_spool_usage (tenant_id, filament_spool_id);

ALTER TABLE hub.filaments
    ADD CONSTRAINT fk_filaments_material_type_tenant
        FOREIGN KEY (tenant_id, material_type_id)
            REFERENCES hub.material_types (tenant_id, id),
    ADD CONSTRAINT fk_filaments_vendor_tenant
        FOREIGN KEY (tenant_id, vendor_id)
            REFERENCES hub.vendors (tenant_id, id);

CREATE FUNCTION hub.validate_print_allocation_group_tenant()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
DECLARE
    preview_tenant_id uuid;
BEGIN
    SELECT printer.tenant_id
    INTO preview_tenant_id
    FROM hub.print_allocation_preview preview
             JOIN hub.print_jobs print_job ON print_job.id = preview.print_job_id
             JOIN hub.printers printer ON printer.id = print_job.printer_id
    WHERE preview.id = NEW.preview_id;

    IF preview_tenant_id IS NULL OR NEW.tenant_id IS DISTINCT FROM preview_tenant_id THEN
        RAISE EXCEPTION 'Print allocation group tenant must match its preview printer tenant';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_print_allocation_group_tenant
    AFTER INSERT OR UPDATE OF tenant_id, preview_id ON hub.print_allocation_group
    DEFERRABLE INITIALLY IMMEDIATE
    FOR EACH ROW
EXECUTE FUNCTION hub.validate_print_allocation_group_tenant();

CREATE FUNCTION hub.validate_print_allocation_item_tenant()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
DECLARE
    spool_tenant_id uuid;
BEGIN
    IF NEW.filament_spool_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT filament.tenant_id
    INTO spool_tenant_id
    FROM hub.filament_spools spool
             JOIN hub.filaments filament ON filament.id = spool.filament_id
    WHERE spool.id = NEW.filament_spool_id;

    IF spool_tenant_id IS NULL OR NEW.tenant_id IS DISTINCT FROM spool_tenant_id THEN
        RAISE EXCEPTION 'Print allocation item tenant must match its spool tenant';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_print_allocation_item_tenant
    AFTER INSERT OR UPDATE OF tenant_id, filament_spool_id ON hub.print_allocation_item
    DEFERRABLE INITIALLY IMMEDIATE
    FOR EACH ROW
EXECUTE FUNCTION hub.validate_print_allocation_item_tenant();

CREATE FUNCTION hub.validate_job_spool_usage_tenant()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
DECLARE
    job_tenant_id uuid;
    spool_tenant_id uuid;
BEGIN
    SELECT printer.tenant_id
    INTO job_tenant_id
    FROM hub.print_jobs print_job
             JOIN hub.printers printer ON printer.id = print_job.printer_id
    WHERE print_job.id = NEW.print_job_id;

    SELECT filament.tenant_id
    INTO spool_tenant_id
    FROM hub.filament_spools spool
             JOIN hub.filaments filament ON filament.id = spool.filament_id
    WHERE spool.id = NEW.filament_spool_id;

    IF job_tenant_id IS NULL
        OR spool_tenant_id IS NULL
        OR NEW.tenant_id IS DISTINCT FROM job_tenant_id
        OR NEW.tenant_id IS DISTINCT FROM spool_tenant_id THEN
        RAISE EXCEPTION 'Job spool usage tenant must match its print job and spool tenants';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_job_spool_usage_tenant
    AFTER INSERT OR UPDATE OF tenant_id, print_job_id, filament_spool_id ON hub.job_spool_usage
    DEFERRABLE INITIALLY IMMEDIATE
    FOR EACH ROW
EXECUTE FUNCTION hub.validate_job_spool_usage_tenant();

DO
$$
DECLARE
    default_tenant_id uuid;
BEGIN
    SELECT id INTO default_tenant_id FROM hub.tenants WHERE is_default;

    IF default_tenant_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM hub.printers WHERE tenant_id = default_tenant_id)
        AND NOT EXISTS (SELECT 1 FROM hub.filaments WHERE tenant_id = default_tenant_id)
        AND NOT EXISTS (SELECT 1 FROM hub.vendors WHERE tenant_id = default_tenant_id)
        AND NOT EXISTS (SELECT 1 FROM hub.material_types WHERE tenant_id = default_tenant_id)
        AND NOT EXISTS (
            SELECT 1
            FROM hub.print_allocation_group allocation_group
            WHERE allocation_group.tenant_id = default_tenant_id
        )
        AND NOT EXISTS (
            SELECT 1
            FROM hub.print_allocation_item allocation_item
            WHERE allocation_item.tenant_id = default_tenant_id
        )
        AND NOT EXISTS (
            SELECT 1
            FROM hub.job_spool_usage usage
            WHERE usage.tenant_id = default_tenant_id
        )
        AND NOT EXISTS (
            SELECT 1
            FROM hub.print_jobs print_job
                     JOIN hub.printers printer ON printer.id = print_job.printer_id
            WHERE printer.tenant_id = default_tenant_id
        )
        AND NOT EXISTS (
            SELECT 1
            FROM hub.maintenance_definition definition
                     JOIN hub.printers printer ON printer.id = definition.printer_id
            WHERE printer.tenant_id = default_tenant_id
        )
        AND NOT EXISTS (
            SELECT 1
            FROM hub.printer_stats statistics
                     JOIN hub.printers printer ON printer.id = statistics.printer_id
            WHERE printer.tenant_id = default_tenant_id
        ) THEN
        DELETE FROM hub.tenants WHERE id = default_tenant_id;
    END IF;
END;
$$;
