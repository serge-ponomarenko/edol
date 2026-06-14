package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelAvailableEventListener {

    private final ModelMetadataWorkflowService modelMetadataWorkflowService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @EventListener
    public void handle(ModelAvailableEvent event) {
        log.info(
                "MODEL AVAILABLE: {}",
                event.modelFile().getFileName()
        );

        UUID printerId = event.printerId();

        try {
            modelMetadataWorkflowService.parseMetadata(printerId, event.modelFile());

            applicationEventPublisher.publishEvent(
                    new MetadataParsedEvent(printerId, event.modelFile().getFileName().toString())
            );

        } catch (Exception e) {
            log.error(
                    "Metadata parsing failed for {}",
                    event.modelFile().getFileName(),
                    e
            );
        }
    }
}