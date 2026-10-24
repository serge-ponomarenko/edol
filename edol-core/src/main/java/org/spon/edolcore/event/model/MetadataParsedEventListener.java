package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.mqtt.CoreIntegrationEventPublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MetadataParsedEventListener {

    private final PrinterStateService printerStateService;
    private final CoreIntegrationEventPublisher mqttMessagePublisher;

    @EventListener
    public void handle(MetadataParsedEvent event) {
        UUID printerId = event.printerId();

        PrinterState state = printerStateService.getState(printerId);

        // Live printing state and camera acquisition do not depend on optional model metadata.

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
