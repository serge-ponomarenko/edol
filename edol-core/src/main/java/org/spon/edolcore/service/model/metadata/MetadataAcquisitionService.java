package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<ScheduledFuture<?>> retryTask = new AtomicReference<>();

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger attempt = new AtomicInteger(0);

    public void start() {
        stop();

        active.set(true);
        attempt.set(0);

        log.info("Starting metadata acquisition");

        attemptAcquisition();
    }

    public void stop() {
        if (!active.get() && retryTask.get() == null) {
            return;
        }

        active.set(false);
        attempt.set(0);

        ScheduledFuture<?> task = retryTask.getAndSet(null);

        if (task != null) {
            task.cancel(false);
        }

        log.info("Stopped metadata acquisition");
    }

    public void retryNow() {
        scheduleRetry(
                new IllegalStateException("Agent model transfer failed")
        );
    }

    private void attemptAcquisition() {
        if (!active.get()) {
            return;
        }

        try {
            log.info(
                    "Attempting metadata acquisition (attempt #{})",
                    attempt.get() + 1
            );

            modelMetadataWorkflowService.requestMetadata();
        } catch (Exception e) {
            scheduleRetry(e);
        }
    }

    private void scheduleRetry(Exception exception) {
        if (!active.get()) {
            return;
        }

        int failedAttempt = attempt.get() + 1;
        long delaySeconds = getRetryDelaySeconds();

        log.warn(
                "Metadata acquisition attempt #{} failed. Retrying in {} seconds",
                failedAttempt,
                delaySeconds,
                exception
        );

        retryTask.set(taskScheduler.schedule(
                        this::attemptAcquisition,
                        java.time.Instant.now().plusSeconds(delaySeconds)
                )
        );
    }

    private long getRetryDelaySeconds() {
        long delay;

        int attemptValue = attempt.get();

        if (attemptValue < RETRY_DELAYS_SECONDS.length) {
            delay = RETRY_DELAYS_SECONDS[attemptValue];
        } else {
            delay = RETRY_DELAYS_SECONDS[RETRY_DELAYS_SECONDS.length - 1];
        }

        attempt.incrementAndGet();

        return delay;
    }

}