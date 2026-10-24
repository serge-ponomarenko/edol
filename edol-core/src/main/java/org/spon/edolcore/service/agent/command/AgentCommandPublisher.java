package org.spon.edolcore.service.agent.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentCommandPublisher {

    private final MqttMessagePublisher mqttMessagePublisher;
    private final PrinterConnectionConfigurationRepository repository;
    private final org.spon.edolcore.service.PrinterStateService printerStateService;
    private final org.springframework.beans.factory.ObjectProvider<org.spon.edolcore.service.agent.mqtt.AgentMqttService> secureMqtt;
    @org.springframework.beans.factory.annotation.Value("${edol.deployment.mode:home}")
    private String mode;

    public void publishSnapshotIfAvailable(UUID printerId, String payload) {
        if (!"secure-multi-tenant".equals(mode)) {
            publish(printerId, payload);
            return;
        }
        // Resolve authority under the current tenant even when the printer is offline.
        var authority = secureMqtt.getIfAvailable();
        if (authority == null) return;
        var topic = authority.availableCommandTopic(printerId, "commands/agent");
        if (topic.isPresent() && printerStateService.getState(printerId).isOnline()) {
            mqttMessagePublisher.publish(topic.get(), payload);
        }
    }

    public void publish(
            UUID printerId,
            String payload
    ) {
        if ("secure-multi-tenant".equals(mode)) {
            mqttMessagePublisher.publish(secureMqtt.getObject().commandTopic(printerId, "commands/agent"), payload);
            return;
        }
        String agentId = repository
                .findByPrinterId(printerId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Configuration not found for printer "
                                        + printerId
                        )
                )
                .getAgentId();

        mqttMessagePublisher.publish(
                "edol/agents/" + agentId + "/commands/agent",
                payload
        );
    }
}
