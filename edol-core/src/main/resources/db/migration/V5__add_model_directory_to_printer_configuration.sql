ALTER TABLE printer_connection_configurations
    ADD COLUMN model_directory VARCHAR(255);

UPDATE printer_connection_configurations
SET model_directory = '';

ALTER TABLE printer_connection_configurations
    ALTER COLUMN model_directory SET NOT NULL;