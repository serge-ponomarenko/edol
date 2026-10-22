package org.spon.edolcoreapi.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureMqttEndpointTest {
    @Test
    void preservesHomeAndRequiresExplicitVerifiedTlsEndpointForSecureClients() {
        assertDoesNotThrow(() -> SecureMqttEndpoint.validate(false, "tcp://mqtt:1883", "", ""));
        assertDoesNotThrow(() -> SecureMqttEndpoint.validate(true, "ssl://mqtt.example.invalid:8883", "service", "secret"));
        for (String url : new String[]{"tcp://mqtt:1883", "ssl://mqtt", "ssl://user:secret@mqtt:8883",
                "ssl://mqtt:8883/path", "ssl://mqtt:8883?query", "ssl://mqtt:8883#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> SecureMqttEndpoint.validate(true, url, "service", "secret"));
        }
        assertThrows(IllegalArgumentException.class, () -> SecureMqttEndpoint.validate(true, "ssl://mqtt:8883", "", ""));
    }
}
