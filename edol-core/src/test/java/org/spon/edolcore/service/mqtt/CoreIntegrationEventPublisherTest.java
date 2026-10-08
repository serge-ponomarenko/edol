package org.spon.edolcore.service.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreIntegrationEventPublisherTest {

    @Test
    void publishesOneAdditiveV2MessageWithUnchangedLegacyFields() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        CoreEventTenantResolver tenantResolver = mock(CoreEventTenantResolver.class);
        UUID printerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(tenantResolver.tenantIdForPrinter(printerId)).thenReturn(Optional.of(tenantId));

        CoreIntegrationEventPublisher publisher = new CoreIntegrationEventPublisher(
                channel,
                tenantResolver,
                new ObjectMapper()
        );

        publisher.publish("edolcore/print/metadata", Map.of(
                "event", "print.metadata.loaded",
                "printerId", printerId,
                "sessionId", "session",
                "fileName", "model.3mf"
        ));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Message<String>> message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(channel).send(message.capture());
        JsonNode json = new ObjectMapper().readTree(message.getValue().getPayload());
        assertThat(json.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(json.path("eventId").asText()).isNotBlank();
        assertThat(json.path("eventType").asText()).isEqualTo("print.metadata.loaded");
        assertThat(json.path("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(json.path("printerId").asText()).isEqualTo(printerId.toString());
        assertThat(json.path("timestamp").asText()).endsWith("Z");
        assertThat(json.path("payload").path("sessionId").asText()).isEqualTo("session");
        assertThat(json.path("event").asText()).isEqualTo("print.metadata.loaded");
        assertThat(json.path("fileName").asText()).isEqualTo("model.3mf");
        assertThat(message.getValue().getHeaders().get("mqtt_qos")).isEqualTo(1);
    }

    @Test
    void doesNotPublishWhenPersistedPrinterOwnershipIsMissing() {
        MessageChannel channel = mock(MessageChannel.class);
        CoreEventTenantResolver tenantResolver = mock(CoreEventTenantResolver.class);
        UUID printerId = UUID.randomUUID();
        when(tenantResolver.tenantIdForPrinter(printerId)).thenReturn(Optional.empty());

        new CoreIntegrationEventPublisher(channel, tenantResolver, new ObjectMapper()).publish(
                "edolcore/printer/online",
                Map.of("event", "printer.online", "printerId", printerId)
        );

        verifyNoInteractions(channel);
    }
}
