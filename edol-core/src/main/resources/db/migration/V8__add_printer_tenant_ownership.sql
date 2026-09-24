ALTER TABLE core.printers
    ADD COLUMN tenant_id uuid;

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM core.printers) THEN
        RETURN;
    END IF;

    IF to_regclass('hub.printers') IS NULL THEN
        RAISE EXCEPTION 'Cannot backfill Core printer tenant ownership: Hub printer projection is unavailable';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM core.printers core_printer
        LEFT JOIN hub.printers hub_printer ON hub_printer.id = core_printer.id
        WHERE hub_printer.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot backfill Core printer tenant ownership: Core printer is missing from Hub projection';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM hub.printers hub_printer
        JOIN core.printers core_printer ON core_printer.id = hub_printer.id
        GROUP BY hub_printer.id
        HAVING COUNT(*) <> 1
    ) THEN
        RAISE EXCEPTION 'Cannot backfill Core printer tenant ownership: Hub projection is duplicated';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM core.printers core_printer
        JOIN hub.printers hub_printer ON hub_printer.id = core_printer.id
        WHERE hub_printer.tenant_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot backfill Core printer tenant ownership: Hub projection tenant is missing';
    END IF;

    UPDATE core.printers core_printer
    SET tenant_id = hub_printer.tenant_id
    FROM hub.printers hub_printer
    WHERE hub_printer.id = core_printer.id
      AND core_printer.tenant_id IS NULL;
END;
$$;

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM core.printers WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot backfill Core printer tenant ownership: tenant backfill is incomplete';
    END IF;
END;
$$;

CREATE INDEX idx_printers_tenant_id ON core.printers (tenant_id);
