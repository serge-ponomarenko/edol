package org.spon.edolcore.service.printer.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.agent.AgentStateService;
import org.spon.edolcore.service.agent.event.AgentEventConsumer;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTelemetryMessageHandler {

    private final AgentTelemetryConsumer agentTelemetryConsumer;
    private final AgentStateService agentStateService;
    private final AgentEventConsumer agentEventConsumer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrinterConnectionConfigurationRepository configurationRepository;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message) throws Exception {

        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");

        Object payload = message.getPayload();

        if (topic == null) {
            return;
        }

        String json = payload.toString();

        UUID printerId =
                resolvePrinterId(
                        extractAgentId(topic)
                );

        if (topic.endsWith("/printer/report")) {
            agentTelemetryConsumer.consume(
                    printerId,
                    json
            );
            return;
        }

        if (topic.endsWith("/events")) {
            agentEventConsumer.consume(
                    printerId,
                    json
            );
            return;
        }

        if (topic.endsWith("/heartbeat")) {
            AgentHeartbeatDto heartbeat =
                    objectMapper.readValue(
                            json,
                            AgentHeartbeatDto.class
                    );

            agentStateService.update(
                    printerId,
                    heartbeat
            );

            log.debug(
                    "Agent heartbeat received: {}",
                    heartbeat.getAgentId()
            );
        }
    }

    private UUID resolvePrinterId(String agentId) {
        return configurationRepository
                .findByAgentId(agentId)
                .map(configuration ->
                        configuration.getPrinter().getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Unknown agent: " + agentId
                        ));
    }

    private String extractAgentId(String topic) {
        String[] parts = topic.split("/");

        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid topic: " + topic
            );
        }

        return parts[2];
    }
}