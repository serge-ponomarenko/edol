package org.spon.edolcore.agent;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.spon.edolcore.service.agent.AgentStateService;
import org.spon.edolcore.service.agent.event.AgentEventConsumer;
import org.spon.edolcore.service.agent.mqtt.AgentMqttService;
import org.spon.edolcore.service.agent.mqtt.AgentMqttTopics;
import org.spon.edolcore.service.agent.mqtt.SecureAgentTelemetryHandler;
import org.spon.edolcore.service.printer.telemetry.AgentTelemetryConsumer;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecureAgentRoutingTest {
    @Test
    void routesOnlyExactActiveIdentityAndClearsTenantOnSuccessAndFailure() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID printer = UUID.randomUUID();
        var mqtt = mock(AgentMqttService.class);
        when(mqtt.ingress(agent)).thenReturn(new AgentMqttService.Binding(agent, tenant, printer, "READY", true));
        var tenants = new CoreTenantContext();
        var telemetry = mock(AgentTelemetryConsumer.class);
        var events = mock(AgentEventConsumer.class);
        var state = mock(AgentStateService.class);
        var handler = new SecureAgentTelemetryHandler(mqtt, tenants, telemetry, events, state);
        doAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(tenant);
            return null;
        }).when(telemetry).consume(eq(printer), anyString());
        handler.handle(message(AgentMqttTopics.topic(agent, "printer/report"), "{\"print\":{}}"));
        verify(telemetry).consume(printer, "{\"print\":{}}");
        assertThat(tenants.hasCurrentTenant()).isFalse();
        for (String field : new String[]{"agentId", "tenantId", "printerId"}) {
            handler.handle(message(AgentMqttTopics.topic(agent, "heartbeat"), "{\"" + field + "\":\"" + UUID.randomUUID() + "\"}"));
        }
        handler.handle(message("edol/agents/" + agent + "/heartbeat", "{}"));
        handler.handle(message(AgentMqttTopics.topic(agent, "commands/agent"), "{}"));
        handler.handle(message("edol/agent/v1/+/heartbeat", "{}"));
        handler.handle(MessageBuilder.withPayload("{}").setHeader("mqtt_receivedTopic", AgentMqttTopics.topic(agent, "heartbeat"))
                .setHeader("mqtt_receivedRetained", true).build());
        verifyNoInteractions(state, events);
        doThrow(new IllegalArgumentException("retired")).when(mqtt).ingress(agent);
        handler.handle(message(AgentMqttTopics.topic(agent, "printer/report"), "{}"));
        verifyNoMoreInteractions(telemetry);
        assertThat(tenants.hasCurrentTenant()).isFalse();
    }

    private static org.springframework.messaging.Message<String> message(String topic, String json) {
        return MessageBuilder.withPayload(json).setHeader("mqtt_receivedTopic", topic).build();
    }
}
