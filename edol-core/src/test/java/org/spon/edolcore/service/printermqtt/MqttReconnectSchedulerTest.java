package org.spon.edolcore.service.printermqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqttReconnectScheduler")
class MqttReconnectSchedulerTest {

    @Mock
    private BambuMqttClient mqttClient;

    @InjectMocks
    private MqttReconnectScheduler scheduler;

    @Nested
    @DisplayName("reconnect")
    class Reconnect {

        @Test
        @DisplayName("calls connect when client is disconnected")
        void callsConnectWhenDisconnected() {
            when(mqttClient.isConnected()).thenReturn(false);

            scheduler.reconnect();

            verify(mqttClient).connect();
        }

        @Test
        @DisplayName("skips connect when client is already connected")
        void skipsConnectWhenAlreadyConnected() {
            when(mqttClient.isConnected()).thenReturn(true);

            scheduler.reconnect();

            verify(mqttClient, never()).connect();
        }
    }
}