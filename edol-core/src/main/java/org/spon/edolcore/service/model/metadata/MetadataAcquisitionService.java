package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataAcquisitionService {

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
    private final LogContextFactory logContextFactory;
    private final PrinterStateService printerStateService;


    public void start(UUID printerId) {
        stop(printerId);

        MetadataAcquisitionState state = state(printerId);

        synchronized (state) {
            state.setActive(true);
            state.setAttempt(0);
        }

        logMessage(printerId, "Starting metadata acquisition for printer");

        attemptAcquisition(printerId);
    }

    public void stop(UUID printerId) {
        MetadataAcquisitionState state = state(printerId);

        ScheduledFuture<?> task;

        synchronized (state) {
            task = state.getRetryTask();

            if (!state.isActive() && task == null) {
                return;
            }

            state.setActive(false);
            state.setAttempt(0);
            state.setRetryTask(null);
        }

        if (task != null) {
            task.cancel(false);
        }

        logMessage(printerId, "Stopped metadata acquisition for printer");
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

        synchronized (state) {
            if (!state.isActive()) {
                return;
            }
        }

        try {
            logMessage(printerId, "Attempting metadata acquisition");

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

        int failedAttempt;
        long delaySeconds;

        synchronized (state) {
            if (!state.isActive()) {
                return;
            }

            failedAttempt = state.getAttempt() + 1;
            delaySeconds = getRetryDelaySeconds(state);
        }

        logContextFactory
                .session(
                        log.atWarn(),
                        printerId,
                        printerStateService.getState(printerId).getSessionId()
                )
                .log(
                        "Metadata acquisition attempt #{} failed. Retrying in {} seconds",
                        failedAttempt,
                        delaySeconds,
                        exception
                );

        ScheduledFuture<?> retryTask =
                taskScheduler.schedule(
                        () -> attemptAcquisition(printerId),
                        java.time.Instant.now()
                                .plusSeconds(delaySeconds)
                );

        synchronized (state) {
            state.setRetryTask(retryTask);
        }
    }

    private long getRetryDelaySeconds(
            MetadataAcquisitionState state
    ) {
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

    private void logMessage(UUID printerId, String message) {
        PrinterState printerState = printerStateService.getState(printerId);
        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerState.getSessionId()
                )
                .log(
                        message
                );
    }

}