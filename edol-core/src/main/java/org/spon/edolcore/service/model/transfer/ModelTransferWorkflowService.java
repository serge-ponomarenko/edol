package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.model.ModelTransferFailedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTransferWorkflowService {

    private static final String PRINTER_ID_KEY = "printerId";
    private static final String MODEL_FILE_NAME_KEY = "modelFileName";

    private final ApplicationEventPublisher applicationEventPublisher;

    public void onUploadStarted(UUID printerId, String fileName) {
        log.atInfo()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue(MODEL_FILE_NAME_KEY, fileName)
                .log("Model upload started");
    }

    public void onUploadCompleted(UUID printerId, String fileName) {
        log.atInfo()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue(MODEL_FILE_NAME_KEY, fileName)
                .log("Model upload completed");
    }

    public void onUploadFailed(
            UUID printerId,
            String fileName,
            String reason
    ) {
        log.atError()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue(MODEL_FILE_NAME_KEY, fileName)
                .addKeyValue("reason", reason)
                .log("Model upload failed");

        applicationEventPublisher.publishEvent(
                new ModelTransferFailedEvent(
                        printerId,
                        fileName,
                        reason
                )
        );
    }
}
