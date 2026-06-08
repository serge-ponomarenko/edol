package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetadataParsedEventListener {

    private final PrinterStateService printerStateService;
    private final MqttMessagePublisher mqttMessagePublisher;
    private final AgentCommandGateway agentCommandGateway;

    @EventListener
    public void handle(MetadataParsedEvent event) {
        PrinterState state = printerStateService.getState();

        state.setPrinting(true);

        agentCommandGateway.enableSnapshotScheduler();

        mqttMessagePublisher.publish(
                "edolcore/print/metadata",
                Map.of(
                        "event", "print.metadata.loaded",
                        "sessionId", state.getSessionId(),
                        "fileName", event.filename()
                )
        );
    }
}