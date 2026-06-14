package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MetadataParsedEventListener {

    private final PrinterStateService printerStateService;
    private final MqttMessagePublisher mqttMessagePublisher;
    private final AgentCommandGateway agentCommandGateway;

    @EventListener
    public void handle(MetadataParsedEvent event) {
        UUID printerId = event.printerId();

        PrinterState state = printerStateService.getState(printerId);

        state.setPrinting(true);

        agentCommandGateway.enableSnapshotScheduler(printerId);

        mqttMessagePublisher.publish(
                "edolcore/print/metadata",
                Map.of(
                        "event", "print.metadata.loaded",
                        "printerId", printerId,
                        "sessionId", state.getSessionId(),
                        "fileName", event.filename()
                )
        );
    }
}