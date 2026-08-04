package org.spon.edolcore.event.printer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ErrorCodes;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.PrinterEvent;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.model.metadata.MetadataAcquisitionService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.print.ActivePrintContext;
import org.spon.edolcore.service.print.ActivePrintContextService;
import org.spon.edolcore.service.print.SpoolFingerprintBuilder;
import org.spon.edolcore.service.print.recovery.RecoveryStartupCoordinator;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeQueryService;
import org.spon.edolcore.service.printer.runtime.PrinterStateRuntime;
import org.spon.edolcore.service.timelapse.TimelapseService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Component
@Slf4j
@RequiredArgsConstructor
public class PrinterEventListener {

    private static final int PROGRESS_LOG_STEP = 5;
    private static final int LAYER_LOG_STEP = 10;
    private static final String EVENT_KEY = "event";
    private static final String PRINTER_ID_KEY = "printerId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String PATH_KEY = "path";
    private static final String ERROR_CODE_KEY = "error-code";
    private static final String ERROR_MESSAGE_KEY = "error-message";
    private static final String PREV_SLOT_KEY = "prev_slot";
    private static final String CURR_SLOT_KEY = "curr_slot";

    private final ModelMetadataWorkflowService modelMetadataWorkflowService;
    private final PrinterStateService printerStateService;
    private final CameraSnapshotStore cameraSnapshotStore;
    private final TimelapseService timelapseService;
    private final MqttMessagePublisher mqttMessagePublisher;
    private final AgentCommandGateway agentCommandGateway;
    private final ActivePrintContextService activePrintContextService;
    private final SpoolFingerprintBuilder spoolFingerprintBuilder;
    private final RecoveryStartupCoordinator recoveryStartupCoordinator;
    private final MetadataAcquisitionService metadataAcquisitionService;
    private final PrinterRuntimeQueryService runtimeQueryService;
    private final LogContextFactory logContextFactory;
    private final ExecutorService timelapseExecutor;
    private final ExecutorService virtualThreadExecutor;

    private PrinterStateRuntime runtime(UUID printerId) {
        return runtimeQueryService
                .getRuntime(printerId)
                .getContext()
                .getPrinterStateRuntime();
    }

    @EventListener
    public void handlePrinterEvent(PrinterEvent event) {
        UUID printerId = event.getPrinterId();
        PrinterState printerState = printerStateService.getState(printerId);

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerState.getSessionId()
                )
                .log(
                        "PRINTER EVENT: {}", event.getType()
                );

        printerStateService.getState(printerId).setError(null);

        switch (event.getType()) {

            case PRINTER_ONLINE -> handlePrinterOnline(printerId);

            case PRINTER_OFFLINE -> handlePrinterOffline(printerId);

            case PRINT_STARTED -> handlePrintStarted(printerId);

            case PRINT_RUNNING -> handlePrintRunning(printerId);

            case PRINT_PAUSED -> handlePrintPaused(printerId);

            case PRINT_FINISHED -> handlePrintFinished(
                    printerId,
                    "edolcore/print/finished",
                    "print.finished"
            );

            case PRINT_FAILED -> handlePrintFinished(
                    printerId,
                    "edolcore/print/failed",
                    "print.failed"
            );

            case PRINT_ERROR -> handlePrintError(printerId);

            case LAYER_CHANGED -> handleLayerChanged(printerId);

            case PROGRESS_CHANGED -> handleProgressChanged(printerId);

            case AMS_STATUS_CHANGED -> handleAmsStatusChanged(printerId);

            case AMS_SLOT_CHANGED -> handleAmdSlotChanged(printerId);

            case FILAMENT_CHANGED -> handleFilamentChanged(printerId);

        }

    }

    private void handlePrinterOnline(UUID printerId) {
        recoveryStartupCoordinator.startRecoveryIfNeeded(printerId);

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/printer/online",
                        payload(
                                printerId,
                                "printer.online"
                        )
                ));
    }

    private void handlePrinterOffline(UUID printerId) {
        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/printer/offline",
                        payload(
                                printerId,
                                "printer.offline"
                        )
                ));
    }

    private void handlePrintStarted(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        runtime(printerId).setLastLogProgressMilestone(-1);

        String sessionId = UUID.randomUUID().toString();

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        sessionId
                )
                .log(
                        "Print started: {}", state.getCurrentTask()
                );

        state.setSessionId(sessionId);
        state.setProgress(0);
        cameraSnapshotStore.setCurrentSessionId(printerId, sessionId);
        modelMetadataWorkflowService.setMetadataLoaded(printerId, false);

        activePrintContextService.deleteByPrinterId(
                printerId
        );

        activePrintContextService.save(
                printerId,
                ActivePrintContext.builder()
                        .printerId(printerId)
                        .sessionId(UUID.fromString(sessionId))
                        .fileName(state.getCurrentFile())
                        .subtaskName(state.getCurrentTask())
                        .totalLayers(state.getTotalLayers())
                        .savedLayer(state.getLayer())
                        .savedProgress(state.getProgress())
                        .remainingTime(state.getRemainingTime())
                        .spoolFingerprint(
                                spoolFingerprintBuilder.build(
                                        state.getAms(),
                                        state.getExtTray()
                                )
                        )
                        .startedAt(java.time.Instant.now())
                        .lastUpdatedAt(java.time.Instant.now())
                        .build()
        );

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/started",
                        payload(
                                printerId,
                                "print.started",
                                SESSION_ID_KEY, sessionId
                        )
                ));

        virtualThreadExecutor.submit(() -> {
            try {
                // Small delay before model acquisition.
                // Some printers may reject FTPS access immediately after PRINT_STARTED.
                Thread.sleep(1000);

                metadataAcquisitionService.start(printerId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logContextFactory
                        .session(
                                log.atWarn(),
                                printerId,
                                sessionId
                        )
                        .log(
                                "Metadata download interrupted", e
                        );
            } catch (Exception e) {
                logContextFactory
                        .session(
                                log.atError(),
                                printerId,
                                sessionId
                        )
                        .log(
                                "Cannot download model!", e
                        );
            }

        });
    }

    private void handlePrintRunning(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        if (state.isPrinting()) {

            mqttMessagePublisher.publish(
                    "edolcore/print/running",
                    payload(
                            printerId,
                            "print.running",
                            SESSION_ID_KEY, state.getSessionId()
                    )
            );

        }
    }

    private void handlePrintPaused(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        mqttMessagePublisher.publish(
                "edolcore/print/paused",
                payload(
                        printerId,
                        "print.paused",
                        SESSION_ID_KEY, state.getSessionId()
                )
        );
    }

    private void handlePrintFinished(UUID printerId, String topic, String eventName) {
        metadataAcquisitionService.stop(printerId);

        PrinterState state = printerStateService.getState(printerId);

        if (state.getSessionId() != null) {
            activePrintContextService.deleteByPrinterId(
                    printerId
            );
        }

        state.setPrinting(false);

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        topic,
                        payload(
                                printerId,
                                eventName,
                                SESSION_ID_KEY, state.getSessionId()
                        )
                ));

        agentCommandGateway.disableSnapshotScheduler(printerId);

        generateTimelapse(printerId, state.getSessionId());
    }

    private void handlePrintError(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);
        Integer errorCode = state.getError().getCode();
        logContextFactory
                .session(
                        log.atError(),
                        printerId,
                        state.getSessionId()
                )
                .log(
                        "❌ Printer error code: {} - {}",
                        errorCode,
                        ErrorCodes.errorMap.get(errorCode)
                );

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/error",
                        payload(
                                printerId,
                                "print.error",
                                ERROR_CODE_KEY, errorCode,
                                ERROR_MESSAGE_KEY, ErrorCodes.errorMap.get(errorCode)
                        )
                ));
    }

    private void handleLayerChanged(UUID printerId) {
        updateActivePrintContext(printerId);

        int layer = printerStateService.getState(printerId).getLayer();
        if (isLayerLogMilestone(printerId, layer)) {
            logContextFactory
                    .session(
                            log.atInfo(),
                            printerId,
                            printerStateService.getState(printerId).getSessionId()
                    )
                    .log(
                            "Layer changed: {}", layer
                    );
        }
    }

    private void handleProgressChanged(UUID printerId) {
        updateActivePrintContext(printerId);

        PrinterState state = printerStateService.getState(printerId);
        int progress = state.getProgress();

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/progress",
                        payload(
                                printerId,
                                "print.progress.changed",
                                SESSION_ID_KEY, state.getSessionId()
                        )
                ));

        if (isProgressLogMilestone(printerId, progress)) {
            logContextFactory
                    .session(
                            log.atInfo(),
                            printerId,
                            state.getSessionId()
                    )
                    .log(
                            "Progress: {}%", progress
                    );
        }
    }


    private void handleAmsStatusChanged(UUID printerId) {
        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/ams",
                        payload(
                                printerId,
                                "ams.status.changed"
                        )
                ));
    }

    private void handleAmdSlotChanged(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        virtualThreadExecutor.submit(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/ams",
                        payload(
                                printerId,
                                "ams.slot.changed",
                                PREV_SLOT_KEY, state.getAms().getPreviousSlot(),
                                CURR_SLOT_KEY, state.getAms().getActiveSlot()
                        )
                ));

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        state.getSessionId()
                )
                .log(
                        "AMS slot changed: {} -> {}",
                        state.getAms().getPreviousSlot(),
                        state.getAms().getActiveSlot()
                );

    }

    private void handleFilamentChanged(UUID printerId) {
        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        printerStateService.getState(printerId).getSessionId()
                )
                .log(
                        "AMS Filament changed"
                );
    }


    private void generateTimelapse(UUID printerId, String sessionId) {
        cameraSnapshotStore.setCurrentSessionId(printerId, "default");

        timelapseExecutor.submit(() -> {
            try {
                File video = timelapseService.generate(printerId, sessionId);
                if (video != null) {
                    mqttMessagePublisher.publish(
                            "edolcore/print/timelapse",
                            payload(
                                    printerId,
                                    "print.timelapse",
                                    PATH_KEY, video.getAbsolutePath()
                            )
                    );
                }
            } catch (Exception e) {
                logContextFactory
                        .session(
                                log.atInfo(),
                                printerId,
                                printerStateService.getState(printerId).getSessionId()
                        )
                        .log(
                                "Timelapse generation failed", e
                        );
            }
        });
    }

    private boolean isProgressLogMilestone(UUID printerId, int progress) {
        int milestone = progress / PROGRESS_LOG_STEP;

        if (milestone > runtime(printerId).getLastLogProgressMilestone() && milestone > 0) {
            runtime(printerId).setLastLogProgressMilestone(milestone);
            return true;
        }

        return false;
    }

    private boolean isLayerLogMilestone(UUID printerId, int layer) {
        int milestone = layer / LAYER_LOG_STEP;

        if (milestone > runtime(printerId).getLastLogLayerMilestone() && milestone > 0) {
            runtime(printerId).setLastLogLayerMilestone(milestone);
            return true;
        }

        return false;
    }

    private void updateActivePrintContext(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        if (state.getSessionId() == null) {
            return;
        }

        activePrintContextService.updateRecoverySnapshot(
                UUID.fromString(state.getSessionId()),
                state.getLayer(),
                state.getTotalLayers(),
                state.getProgress(),
                state.getRemainingTime()
        );

    }

    private Map<String, Object> payload(
            UUID printerId,
            String eventName
    ) {
        return Map.of(
                PRINTER_ID_KEY, printerId,
                EVENT_KEY, eventName
        );
    }

    private Map<String, Object> payload(
            UUID printerId,
            String eventName,
            String key1, Object value1) {
        return Map.of(
                PRINTER_ID_KEY, printerId,
                EVENT_KEY, eventName,
                key1, value1
        );
    }

    private Map<String, Object> payload(
            UUID printerId,
            String eventName,
            String key1, Object value1,
            String key2, Object value2
    ) {
        return Map.of(
                PRINTER_ID_KEY, printerId,
                EVENT_KEY, eventName,
                key1, value1,
                key2, value2
        );
    }

}
