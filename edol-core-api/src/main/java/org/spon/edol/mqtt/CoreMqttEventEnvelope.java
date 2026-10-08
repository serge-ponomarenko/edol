package org.spon.edol.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.UUID;

/** Versioned Core integration-event envelope; legacy top-level fields remain additive during migration. */
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
}
