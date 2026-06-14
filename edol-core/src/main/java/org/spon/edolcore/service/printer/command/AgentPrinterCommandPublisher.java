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

    public void publish(
            UUID printerId,
            String payload
    ) {
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