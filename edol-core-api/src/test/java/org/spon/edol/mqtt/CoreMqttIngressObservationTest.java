package org.spon.edol.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreMqttIngressObservationTest {

    @Test
    void countsDeliveriesByFixedCategoriesAndIdentifiesCounterRestarts() {
        var observation = new CoreMqttIngressObservation();
        var initial = observation.snapshot();
        assertEquals(0, initial.validatedV2());
        assertEquals(0, initial.validatedV3());
        assertEquals(0, initial.rejectedEnvelope());
        assertEquals(0, initial.processingFailures());
        observation.validated(2, true);
        observation.validated(2, true);
        observation.validated(3, false);
        observation.rejectEnvelope();
        observation.processingFailed();
        var current = observation.snapshot();
        assertEquals(initial.observationId(), current.observationId());
        assertEquals(2, current.validatedV2());
        assertEquals(1, current.validatedV3());
        assertEquals(2, current.withLegacyEvent());
        assertEquals(1, current.rejectedEnvelope());
        assertEquals(1, current.processingFailures());
        assertNotEquals(current.observationId(), new CoreMqttIngressObservation().snapshot().observationId());
    }

    @Test
    void doesNotCreateCounterDimensionsForArbitraryVersions() {
        var observation = new CoreMqttIngressObservation();
        assertThrows(IllegalArgumentException.class, () -> observation.validated(999, false));
        assertEquals(0, observation.snapshot().validatedV2());
        assertEquals(0, observation.snapshot().validatedV3());
    }
}
