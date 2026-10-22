package org.spon.edolcore.service.printer.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentPrinterCommandPublisher {

    private final MqttMessagePublisher mqttMessagePublisher;
    private final PrinterConnectionConfigurationRepository
            connectionConfigurationRepository;
    private final org.springframework.beans.factory.ObjectProvider<org.spon.edolcore.service.agent.mqtt.AgentMqttService> secureMqtt;
    @org.springframework.beans.factory.annotation.Value("${edol.deployment.mode:home}")
    private String mode;

    public void publish(
            UUID printerId,
            String payload
    ) {
        if ("secure-multi-tenant".equals(mode)) {
            mqttMessagePublisher.publish(secureMqtt.getObject().commandTopic(printerId, "commands/printer"), payload);
            return;
        }
        PrinterConnectionConfiguration configuration =
                connectionConfigurationRepository
                        .findByPrinterId(printerId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Missing connection configuration for printer "
                                                + printerId
                                ));

        mqttMessagePublisher.publish(
                "edol/agents/"
                        + configuration.getAgentId()
                        + "/commands/printer",
                payload
        );
    }
}
