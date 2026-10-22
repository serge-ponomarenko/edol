package org.spon.edolcore.service.agent.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.spon.edolcore.service.agent.AgentStateService;
import org.spon.edolcore.service.agent.event.AgentEventConsumer;
import org.spon.edolcore.service.printer.telemetry.AgentTelemetryConsumer;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}")
public class SecureAgentTelemetryHandler {
    private final AgentMqttService mqtt;
    private final CoreTenantContext tenants;
    private final AgentTelemetryConsumer telemetry;
    private final AgentEventConsumer events;
    private final AgentStateService state;
    private final ObjectMapper mapper = new ObjectMapper();

    public SecureAgentTelemetryHandler(AgentMqttService mqtt, CoreTenantContext tenants,
            AgentTelemetryConsumer telemetry, AgentEventConsumer events, AgentStateService state) {
        this.mqtt = mqtt;
        this.tenants = tenants;
        this.telemetry = telemetry;
        this.events = events;
        this.state = state;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message) {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            var binding = mqtt.ingress(AgentMqttTopics.ingressId(topic));
            if (Boolean.TRUE.equals(message.getHeaders().get("mqtt_receivedRetained"))) {
                throw new IllegalArgumentException("Retained agent report rejected");
            }
            String json = message.getPayload().toString();
            if (json.length() > 8192) throw new IllegalArgumentException("Oversized agent report");
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid agent report");
            identity(root, "agentId", binding.agentId().toString());
            identity(root, "printerId", binding.printerId().toString());
            identity(root, "tenantId", binding.tenantId().toString());
            try (var ignored = tenants.open(binding.tenantId())) {
                if (topic.endsWith("/printer/report")) telemetry.consume(binding.printerId(), json);
                else if (topic.endsWith("/events")) events.consume(binding.printerId(), json);
                else state.update(binding.printerId(), mapper.treeToValue(root, AgentHeartbeatDto.class));
            }
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(SecureAgentTelemetryHandler.class).warn("Secure agent MQTT report rejected");
        }
    }

    private static void identity(JsonNode root, String field, String expected) {
        if (root.has(field) && (!root.get(field).isTextual() || !expected.equals(root.get(field).asText()))) {
            throw new IllegalArgumentException("Agent report identity mismatch");
        }
    }
}
