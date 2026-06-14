package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.ModelTransferFailedEvent;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTransferWorkflowService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final PrinterStateService printerStateService;
    private final LogContextFactory logContextFactory;

    public void onUploadStarted(UUID printerId, String fileName) {
        PrinterState printerState = printerStateService.getState(printerId);
        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerState.getSessionId()
                )
                .log(
                        "Model upload started: {}", fileName
                );
    }

    public void onUploadCompleted(UUID printerId, String fileName) {
        PrinterState printerState = printerStateService.getState(printerId);
        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerState.getSessionId()
                )
                .log(
                        "Model upload completed: {}", fileName
                );
    }

    public void onUploadFailed(
            UUID printerId,
            String fileName,
            String reason
    ) {
        PrinterState printerState = printerStateService.getState(printerId);
        logContextFactory
                .session(
                        log.atError(),
                        printerId,
                        printerState.getSessionId()
                )
                .log(
                        "Model upload failed: {}", reason
                );

        applicationEventPublisher.publishEvent(
                new ModelTransferFailedEvent(
                        printerId,
                        fileName,
                        reason
                )
        );
    }
}
