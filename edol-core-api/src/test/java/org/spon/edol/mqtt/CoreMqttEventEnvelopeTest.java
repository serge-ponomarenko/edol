package org.spon.edol.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreMqttEventEnvelopeTest {

    @Test
    void acceptsAdditiveV2AndV3WithoutLegacyFields() {
        assertDoesNotThrow(() -> envelope(2).validate("printer.online"));
        assertDoesNotThrow(() -> envelope(3).validate(null));
        assertDoesNotThrow(() -> envelope(3).validate("printer.online"));
    }

    @Test
    void requiresTheLegacyMarkerForV2AndRejectsContradictoryMarkersForEitherVersion() {
        assertThrows(IllegalArgumentException.class, () -> envelope(2).validate(null));
        assertThrows(IllegalArgumentException.class, () -> envelope(2).validate("printer.offline"));
        assertThrows(IllegalArgumentException.class, () -> envelope(3).validate("printer.offline"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 4, Integer.MAX_VALUE})
    void rejectsUnsupportedVersions(int version) {
        assertThrows(IllegalArgumentException.class, () -> envelope(version).validate("printer.online"));
    }

    @Test
    void rejectsMissingIdentityPayloadAndInvalidTimestamp() {
        CoreMqttEventEnvelope valid = envelope(3);
        for (CoreMqttEventEnvelope invalid : new CoreMqttEventEnvelope[]{
                new CoreMqttEventEnvelope(3, null, valid.eventType(), valid.tenantId(), valid.printerId(), valid.timestamp(), Map.of()),
                new CoreMqttEventEnvelope(3, valid.eventId(), " ", valid.tenantId(), valid.printerId(), valid.timestamp(), Map.of()),
                new CoreMqttEventEnvelope(3, valid.eventId(), valid.eventType(), null, valid.printerId(), valid.timestamp(), Map.of()),
                new CoreMqttEventEnvelope(3, valid.eventId(), valid.eventType(), valid.tenantId(), null, valid.timestamp(), Map.of()),
                new CoreMqttEventEnvelope(3, valid.eventId(), valid.eventType(), valid.tenantId(), valid.printerId(), null, Map.of()),
                new CoreMqttEventEnvelope(3, valid.eventId(), valid.eventType(), valid.tenantId(), valid.printerId(), valid.timestamp(), null)
        }) {
            assertThrows(IllegalArgumentException.class, () -> invalid.validate(null));
        }
        var invalidTime = new CoreMqttEventEnvelope(3, valid.eventId(), valid.eventType(), valid.tenantId(),
                valid.printerId(), "invalid", Map.of());
        assertThrows(DateTimeException.class, () -> invalidTime.validate(null));
    }

    private CoreMqttEventEnvelope envelope(int version) {
        return new CoreMqttEventEnvelope(version, UUID.randomUUID(), "printer.online", UUID.randomUUID(),
                UUID.randomUUID(), "2026-10-21T12:00:00Z", Map.of());
    }
}
