ALTER TABLE active_print_context
    ADD COLUMN printer_id UUID;

UPDATE active_print_context
SET printer_id = (
    SELECT id
    FROM printers
    ORDER BY display_id
    LIMIT 1
);

ALTER TABLE active_print_context
    ALTER COLUMN printer_id SET NOT NULL;

ALTER TABLE active_print_context
    ADD CONSTRAINT uq_active_print_context_printer
        UNIQUE (printer_id);