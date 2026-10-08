CREATE TABLE hub.core_mqtt_event_receipts
(
    event_id    uuid                        NOT NULL PRIMARY KEY,
    tenant_id   uuid                        NOT NULL,
    printer_id  uuid                        NOT NULL,
    event_type  varchar(128)                NOT NULL,
    occurred_at timestamp with time zone    NOT NULL,
    received_at timestamp with time zone    NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_core_mqtt_event_receipts_printer_tenant
        FOREIGN KEY (tenant_id, printer_id) REFERENCES hub.printers (tenant_id, id)
);

CREATE INDEX idx_core_mqtt_event_receipts_tenant_printer_occurred
    ON hub.core_mqtt_event_receipts (tenant_id, printer_id, occurred_at);

ALTER TABLE hub.core_mqtt_event_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.core_mqtt_event_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY core_mqtt_event_receipts_tenant_isolation ON hub.core_mqtt_event_receipts
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());
