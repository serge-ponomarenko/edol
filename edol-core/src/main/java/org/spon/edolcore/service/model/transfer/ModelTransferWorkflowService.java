package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.model.ModelTransferFailedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTransferWorkflowService {

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final ApplicationEventPublisher applicationEventPublisher;

    private volatile int retryCount;

    public void onUploadStarted(String fileName) {
        log.info(
                "Model upload started: {}",
                fileName
        );
    }

    public void onUploadCompleted(String fileName) {
        log.info(
                "Model upload completed: {}",
                fileName
        );

        retryCount = 0;
    }

    public void onUploadFailed(
            String fileName,
            String reason
    ) {
        log.error(
                "Model upload failed: {} ({})",
                fileName,
                reason
        );

        applicationEventPublisher.publishEvent(
                new ModelTransferFailedEvent(
                        fileName,
                        reason
                )
        );
    }
}