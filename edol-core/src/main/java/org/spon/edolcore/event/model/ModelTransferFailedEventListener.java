package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.MetadataAcquisitionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelTransferFailedEventListener {

    private final MetadataAcquisitionService metadataAcquisitionService;
    private final PrinterStateService printerStateService;
    private final LogContextFactory logContextFactory;

    @EventListener
    public void handle(ModelTransferFailedEvent event) {
        UUID printerId = event.printerId();
        PrinterState state = printerStateService.getState(printerId);
        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        state.getSessionId()
                )
                .log(
                        "Model transfer failed for {}. Reason: {}",
                        event.fileName(),
                        event.reason()
                );

        metadataAcquisitionService.retryNow(
                printerId
        );
    }

}