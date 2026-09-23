DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM hub.print_jobs job
        LEFT JOIN hub.printers printer ON printer.id = job.printer_id_uuid
        WHERE job.printer_id_uuid IS NULL OR printer.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot contract print job printer ownership: null or orphaned UUID mapping exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM hub.maintenance_definition definition
        LEFT JOIN hub.printers printer ON printer.id = definition.printer_id
        WHERE definition.printer_id IS NULL OR printer.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot contract maintenance ownership: null or orphaned printer mapping exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM hub.printer_stats statistics
        LEFT JOIN hub.printers printer ON printer.id = statistics.printer_id
        WHERE statistics.printer_id IS NULL OR printer.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot contract printer statistics ownership: null or orphaned printer mapping exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM hub.printer_stats
        GROUP BY printer_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot contract printer statistics ownership: duplicate printer mappings exist';
    END IF;
END;
$$;

ALTER TABLE hub.maintenance_definition
    ALTER COLUMN printer_id SET NOT NULL;

ALTER TABLE hub.printer_stats
    ALTER COLUMN printer_id SET NOT NULL;

ALTER TABLE hub.print_jobs
    ALTER COLUMN printer_id_uuid SET NOT NULL;

ALTER TABLE hub.print_jobs
    DROP COLUMN printer_id;

ALTER TABLE hub.print_jobs
    RENAME COLUMN printer_id_uuid TO printer_id;

ALTER TABLE hub.print_jobs
    RENAME CONSTRAINT fk_print_jobs_printer_uuid TO fk_print_jobs_printer;

ALTER INDEX hub.idx_print_jobs_printer_uuid
    RENAME TO idx_print_jobs_printer;
