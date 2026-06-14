ALTER TABLE printer_connection_configurations
    ADD CONSTRAINT uk_printer_connection_configurations_agent_id
        UNIQUE (agent_id);