package org.spon.edol.mqtt;

import java.util.UUID;

/** Process-local delivery counters, without message content or unbounded dimensions. */
public final class CoreMqttIngressObservation {

    private final UUID observationId = UUID.randomUUID();
    private long validatedV2;
    private long validatedV3;
    private long withLegacyEvent;
    private long rejectedEnvelope;
    private long processingFailures;

    public synchronized void validated(int version, boolean legacyEventPresent) {
        switch (version) {
            case 2 -> validatedV2++;
            case 3 -> validatedV3++;
            default -> throw new IllegalArgumentException("Only validated Core MQTT versions can be counted");
        }
        if (legacyEventPresent) {
            withLegacyEvent++;
        }
    }

    public synchronized void rejectEnvelope() {
        rejectedEnvelope++;
    }

    public synchronized void processingFailed() {
        processingFailures++;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(observationId, validatedV2, validatedV3, withLegacyEvent,
                rejectedEnvelope, processingFailures);
    }

    public record Snapshot(UUID observationId, long validatedV2, long validatedV3,
                           long withLegacyEvent, long rejectedEnvelope, long processingFailures) {
    }
}
