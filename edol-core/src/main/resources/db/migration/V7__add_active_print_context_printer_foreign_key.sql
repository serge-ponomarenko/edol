DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM active_print_context active_context
        LEFT JOIN printers printer ON printer.id = active_context.printer_id
        WHERE printer.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot add active_print_context printer foreign key: orphaned printer_id exists';
    END IF;
END;
$$;

ALTER TABLE active_print_context
    ADD CONSTRAINT fk_active_print_context_printer
        FOREIGN KEY (printer_id)
            REFERENCES printers (id);
