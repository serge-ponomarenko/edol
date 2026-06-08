package org.spon.edolcore.event.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ModelTransferFailedEventListener {

    @EventListener
    public void handle(ModelTransferFailedEvent event) {
        log.error(
                "Model transfer failed for {}. Reason: {}",
                event.fileName(),
                event.reason()
        );
    }
}