package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.PrinterRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class CoreMqttEventReceiptService {

    private final JdbcClient jdbcClient;
    private final PrinterRepository printerRepository;

    @Transactional
    public boolean process(CoreMqttEventEnvelope envelope, Runnable mutation) {
        Printer printer = printerRepository.findById(envelope.printerId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown Hub printer: " + envelope.printerId()));
        if (!envelope.tenantId().equals(printer.getTenantId())) {
            throw new IllegalArgumentException("Core MQTT tenant does not own Hub printer: " + envelope.printerId());
        }

        int inserted = jdbcClient.sql("""
                        insert into hub.core_mqtt_event_receipts
                            (event_id, tenant_id, printer_id, event_type, occurred_at)
                        values (:eventId, :tenantId, :printerId, :eventType, :occurredAt)
                        on conflict (event_id) do nothing
                        """)
                .param("eventId", envelope.eventId())
                .param("tenantId", envelope.tenantId())
                .param("printerId", envelope.printerId())
                .param("eventType", envelope.eventType())
                .param("occurredAt", Instant.parse(envelope.timestamp()).atOffset(ZoneOffset.UTC))
                .update();
        if (inserted == 0) {
            return false;
        }

        mutation.run();
        return true;
    }
}
