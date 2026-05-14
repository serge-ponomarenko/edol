package org.spon.edolcore.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.MqttEventPublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class AmsEventListener {

    private final PrinterStateService printerStateService;
    private final MqttEventPublisher mqttEventPublisher;

    @EventListener
    public void handleAmsEvent(AmsEvent event) {
        log.info("AMS EVENT: {}", event.getType());

        printerStateService.getState().setError(null);

        switch (event.getType()) {

            case AMS_SLOT_UNLOADED -> {
                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/ams",
                                Map.of(
                                        "event", "ams.slot.unloaded",
                                        "slot", event.getSlot()
                                )
                        ));
            }

            case AMS_SLOT_LOADED -> {
                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/ams",
                                Map.of(
                                        "event", "ams.slot.loaded",
                                        "slot", event.getSlot()
                                )
                        ));
            }

        }

    }

}
