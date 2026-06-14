package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
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
    private final LogContextFactory logContextFactory;
    private final PrinterStateService printerStateService;

    @EventListener
    public void handle(ModelAvailableEvent event) {
        UUID printerId = event.printerId();

        PrinterState state = printerStateService.getState(printerId);

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        state.getSessionId()
                )
                .log(
                        "MODEL AVAILABLE: {}",
                        event.modelFile().getFileName()
                );

        try {
            modelMetadataWorkflowService.parseMetadata(printerId, event.modelFile());

            applicationEventPublisher.publishEvent(
                    new MetadataParsedEvent(printerId, event.modelFile().getFileName().toString())
            );

        } catch (Exception e) {
            logContextFactory
                    .session(
                            log.atError(),
                            printerId,
                            state.getSessionId()
                    )
                    .log(
                            "Metadata parsing failed for {}",
                            event.modelFile().getFileName(),
                            e
                    );

        }
    }
}