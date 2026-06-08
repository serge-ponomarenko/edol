package org.spon.edolcore.service.printer.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.spon.edolcore.service.agent.AgentStateService;
import org.spon.edolcore.service.agent.event.AgentEventConsumer;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTelemetryMessageHandler {

    private final AgentTelemetryConsumer agentTelemetryConsumer;
    private final AgentStateService agentStateService;
    private final AgentEventConsumer agentEventConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message) throws Exception {

        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");

        Object payload = message.getPayload();

        if (topic == null) {
            return;
        }

        String json = payload.toString();

        if (topic.endsWith("/printer/report")) {
            agentTelemetryConsumer.consume(json);
            return;
        }

        if (topic.endsWith("/events")) {
            agentEventConsumer.consume(json);
            return;
        }

        if (topic.endsWith("/heartbeat")) {
            AgentHeartbeatDto heartbeat =
                    objectMapper.readValue(
                            json,
                            AgentHeartbeatDto.class
                    );

            agentStateService.update(heartbeat);

            log.debug(
                    "Agent heartbeat received: {}",
                    heartbeat.getAgentId()
            );
        }
    }
}