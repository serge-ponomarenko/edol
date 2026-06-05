package org.spon.edolcore.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqttEventPublisher")
class MqttEventPublisherTest {

    @Mock
    private MessageChannel mqttOutboundChannel;

    private MqttEventPublisher publisher;

    @Captor
    private ArgumentCaptor<Message<String>> messageCaptor;

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("serializes payload to JSON and sends via MessageChannel with MQTT topic header")
        void publishesJsonMessage() {
            publisher = new MqttEventPublisher(mqttOutboundChannel);

            Map<String, Object> payload = Map.of(
                    "event", "print.started",
                    "sessionId", "test-session"
            );

            publisher.publish("edolcore/print/test", payload);

            verify(mqttOutboundChannel).send(messageCaptor.capture());

            Message<String> message = messageCaptor.getValue();
            assertThat(message.getPayload()).contains("\"event\":\"print.started\"");
            assertThat(message.getPayload()).contains("\"sessionId\":\"test-session\"");
            assertThat(message.getHeaders()).containsEntry(MqttHeaders.TOPIC, "edolcore/print/test");
        }

        @Test
        @DisplayName("publishes payload with integer values")
        void publishesIntegerValues() {
            publisher = new MqttEventPublisher(mqttOutboundChannel);

            Map<String, Object> payload = Map.of(
                    "slot", 2,
                    "event", "ams.slot.unloaded"
            );

            publisher.publish("edolcore/ams", payload);

            verify(mqttOutboundChannel).send(messageCaptor.capture());

            Message<String> message = messageCaptor.getValue();
            assertThat(message.getPayload()).contains("\"slot\":2");
            assertThat(message.getHeaders()).containsEntry(MqttHeaders.TOPIC, "edolcore/ams");
        }
    }
}