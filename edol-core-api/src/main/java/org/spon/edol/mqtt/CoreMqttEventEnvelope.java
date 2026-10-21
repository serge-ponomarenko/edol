package org.spon.edol.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/** Secure readers accept additive v2 and envelope-only v3; the producer still publishes v2. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoreMqttEventEnvelope(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID printerId,
        String timestamp,
        Map<String, Object> payload
) {
    public void validate(String legacyEvent) {
        if ((schemaVersion != 2 && schemaVersion != 3)
                || eventId == null || eventType == null || eventType.isBlank()
                || tenantId == null || printerId == null || timestamp == null || payload == null) {
            throw new IllegalArgumentException("Unsupported or incomplete Core MQTT envelope");
        }
        // Parsing validates the timestamp; rejection is driven by the parsing exception.
        Instant.parse(timestamp);
        if ((schemaVersion == 2 || legacyEvent != null) && !eventType.equals(legacyEvent)) {
            throw new IllegalArgumentException("Core MQTT legacy and envelope event types disagree");
        }
    }
}
