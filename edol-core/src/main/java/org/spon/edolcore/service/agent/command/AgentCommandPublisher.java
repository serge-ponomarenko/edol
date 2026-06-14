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

    public void publish(
            UUID printerId,
            String payload
    ) {
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