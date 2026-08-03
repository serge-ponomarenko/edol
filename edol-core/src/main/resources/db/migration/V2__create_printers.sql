CREATE TABLE printers
(
    id              UUID PRIMARY KEY,
    display_id      VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    serial_number   VARCHAR(255) NOT NULL UNIQUE,
    model           VARCHAR(100) NOT NULL,
    connection_mode VARCHAR(100) NOT NULL,
    camera_provider VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE TABLE printer_connection_configurations
(
    id          UUID PRIMARY KEY,
    printer_id  UUID NOT NULL UNIQUE,
    mqtt_host   VARCHAR(255),
    mqtt_port   INTEGER,
    ftp_host    VARCHAR(255),
    ftp_port    INTEGER,
    access_code VARCHAR(255),
    agent_id    VARCHAR(255),

    CONSTRAINT fk_printer_connection_configuration_printer
        FOREIGN KEY (printer_id)
            REFERENCES printers (id)
);