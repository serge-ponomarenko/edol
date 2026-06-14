package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataAcquisitionService {

    private static final String PRINTER_ID_KEY = "printerId";
    private static final long[] RETRY_DELAYS_SECONDS = {
            30,
            60,
            120,
            300
    };

    private final ModelMetadataWorkflowService modelMetadataWorkflowService;
    private final TaskScheduler taskScheduler;

    private final ConcurrentHashMap<UUID, MetadataAcquisitionState> states =
            new ConcurrentHashMap<>();


    public void start(UUID printerId) {
        stop(printerId);

        MetadataAcquisitionState state = state(printerId);

        state.setActive(true);
        state.setAttempt(0);

        log.atInfo()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .log("Starting metadata acquisition for printer");

        attemptAcquisition(printerId);
    }

    public void stop(UUID printerId) {
        MetadataAcquisitionState state = state(printerId);

        ScheduledFuture<?> task = state.getRetryTask();

        if (!state.isActive() && task == null) {
            return;
        }

        state.setActive(false);
        state.setAttempt(0);
        state.setRetryTask(null);

        if (task != null) {
            task.cancel(false);
        }

        log.atInfo()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .log("Stopped metadata acquisition for printer");
    }

    public void retryNow(UUID printerId) {
        scheduleRetry(
                printerId,
                new IllegalStateException(
                        "Agent model transfer failed"
                )
        );
    }

    private void attemptAcquisition(UUID printerId) {
        MetadataAcquisitionState state = state(printerId);

        if (!state.isActive()) {
            return;
        }

        try {
            log.atInfo()
                    .addKeyValue(PRINTER_ID_KEY, printerId)
                    .addKeyValue("attempt", state.getAttempt() + 1)
                    .log("Attempting metadata acquisition");

            modelMetadataWorkflowService.requestMetadata(
                    printerId
            );

        } catch (Exception e) {
            scheduleRetry(
                    printerId,
                    e
            );
        }
    }

    private void scheduleRetry(
            UUID printerId,
            Exception exception
    ) {
        MetadataAcquisitionState state = state(printerId);

        if (!state.isActive()) {
            return;
        }

        int failedAttempt = state.getAttempt() + 1;

        long delaySeconds =
                getRetryDelaySeconds(printerId);

        log.atWarn()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue("attempt", failedAttempt)
                .addKeyValue("retryingIn", delaySeconds)
                .addKeyValue("exception", exception)
                .log("Metadata acquisition failed");

        state.setRetryTask(
                taskScheduler.schedule(
                        () -> attemptAcquisition(printerId),
                        java.time.Instant.now()
                                .plusSeconds(delaySeconds)
                )
        );
    }

    private long getRetryDelaySeconds(
            UUID printerId
    ) {
        MetadataAcquisitionState state =
                state(printerId);

        long delay;

        int attempt = state.getAttempt();

        if (attempt < RETRY_DELAYS_SECONDS.length) {
            delay = RETRY_DELAYS_SECONDS[attempt];
        } else {
            delay = RETRY_DELAYS_SECONDS[
                    RETRY_DELAYS_SECONDS.length - 1
                    ];
        }

        state.setAttempt(attempt + 1);

        return delay;
    }

    private MetadataAcquisitionState state(UUID printerId) {
        return states.computeIfAbsent(
                printerId,
                id -> new MetadataAcquisitionState()
        );
    }

}