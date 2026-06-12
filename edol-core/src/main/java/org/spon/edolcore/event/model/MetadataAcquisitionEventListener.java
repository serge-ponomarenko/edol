package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.model.metadata.MetadataAcquisitionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataAcquisitionEventListener {

    private final MetadataAcquisitionService metadataAcquisitionService;

    @EventListener
    public void handle(MetadataParsedEvent event) {
        metadataAcquisitionService.stop();
    }
}