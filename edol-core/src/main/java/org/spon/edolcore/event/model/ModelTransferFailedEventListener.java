package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.model.metadata.MetadataAcquisitionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelTransferFailedEventListener {

    private final MetadataAcquisitionService metadataAcquisitionService;

    @EventListener
    public void handle(ModelTransferFailedEvent event) {
        log.error(
                "Model transfer failed for {}. Reason: {}",
                event.fileName(),
                event.reason()
        );

        metadataAcquisitionService.retryNow(
                event.printerId()
        );
    }

}