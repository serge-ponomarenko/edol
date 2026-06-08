package org.spon.edolcore.service.printer.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentPrinterCommandPublisher {

    @Value("${edol.agent.id}")
    private String agentId;

    private final MqttMessagePublisher mqttMessagePublisher;

    public void publish(String payload) {
        mqttMessagePublisher.publish(
                "edol/agents/" + agentId + "/commands/printer",
                payload
        );
    }
}