package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
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
    private final CoreRuntimeTenantExecutor runtimeTenantExecutor;


    public void start(UUID printerId) {
        stop(printerId);

        MetadataAcquisitionState state = state(printerId);

        synchronized (state) {
            state.setGeneration(state.getGeneration() + 1);
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
        runtimeTenantExecutor.execute(printerId, () -> attemptInTenant(printerId));
    }

    private void attemptInTenant(UUID printerId) {
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

        synchronized (state) {
            if (!state.isActive() || state.getRetryTask() != null) return;
            if (state.getAttempt() >= RETRY_DELAYS_SECONDS.length) {
                state.setActive(false);
                logMessage(printerId, "Metadata acquisition retry limit reached");
                return;
            }
            long delaySeconds = RETRY_DELAYS_SECONDS[state.getAttempt()];
            state.setAttempt(state.getAttempt() + 1);
            logContextFactory.session(log.atWarn(), printerId,
                            printerStateService.getState(printerId).getSessionId())
                    .log("Metadata acquisition attempt #{} failed. Retrying in {} seconds; category={}",
                            state.getAttempt(), delaySeconds, exception.getClass().getSimpleName());
            long generation = state.getGeneration();
            state.setRetryTask(taskScheduler.schedule(() -> {
                synchronized (state) {
                    if (state.getGeneration() != generation) return;
                    state.setRetryTask(null);
                    if (!state.isActive()) return;
                }
                attemptAcquisition(printerId);
            }, java.time.Instant.now().plusSeconds(delaySeconds)));
        }
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
