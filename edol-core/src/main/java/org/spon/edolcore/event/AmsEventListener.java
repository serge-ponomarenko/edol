package org.spon.edolcore.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmsEventListener {

    private final PrinterStateService printerStateService;
    private final MqttMessagePublisher mqttMessagePublisher;
    private final LogContextFactory logContextFactory;

    @EventListener
    public void handleAmsEvent(AmsEvent event) {
        UUID printerId = event.getPrinterId();

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerStateService.getState(printerId).getSessionId()
                )
                .log(
                        "AMS EVENT: {}", event.getType()
                );


        printerStateService.getState(printerId).setError(null);

        switch (event.getType()) {
            case AMS_SLOT_UNLOADED -> CompletableFuture.runAsync(() ->
                    mqttMessagePublisher.publish(
                            "edolcore/ams",
                            Map.of(
                                    "printerId", printerId,
                                    "event", "ams.slot.unloaded",
                                    "slot", event.getSlot()
                            )
                    ));


            case AMS_SLOT_LOADED -> CompletableFuture.runAsync(() ->
                    mqttMessagePublisher.publish(
                            "edolcore/ams",
                            Map.of(
                                    "printerId", printerId,
                                    "event", "ams.slot.loaded",
                                    "slot", event.getSlot()
                            )
                    ));

            default -> {
                // Nothing
            }

        }

    }

}
