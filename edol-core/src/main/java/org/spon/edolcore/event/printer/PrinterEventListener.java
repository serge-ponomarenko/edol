package org.spon.edolcore.event.printer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ErrorCodes;
import org.spon.edol.model.PrinterError;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.PrinterEvent;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.timelapse.TimelapseService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class PrinterEventListener {

    private static final int PROGRESS_LOG_STEP = 5;
    private static final int LAYER_LOG_STEP = 10;
    private static final String EVENT_KEY = "event";
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

    private int lastLogProgressMilestone = -1;
    private int lastLogLayerMilestone = -1;

    @EventListener
    public void handlePrinterEvent(PrinterEvent event) {

        log.info("PRINTER EVENT: {}", event.getType());

        printerStateService.getState().setError(null);

        switch (event.getType()) {

            case PRINTER_ONLINE -> handlePrinterOnline();

            case PRINTER_OFFLINE -> handlePrinterOffline();

            case PRINT_STARTED -> handlePrintStarted(event);

            case PRINT_RUNNING -> handlePrintRunning();

            case PRINT_PAUSED -> handlePrintPaused();

            case PRINT_FINISHED -> handlePrintFinished(
                    "edolcore/print/finished",
                    "print.finished"
            );

            case PRINT_FAILED -> handlePrintFinished(
                    "edolcore/print/failed",
                    "print.failed"
            );

            case PRINT_ERROR -> handlePrintError(event);

            case LAYER_CHANGED -> handleLayerChanged(event);

            case PROGRESS_CHANGED -> handleProgressChanged(event);

            case AMS_STATUS_CHANGED -> handleAmsStatusChanged();

            case AMS_SLOT_CHANGED -> handleAmdSlotChanged();

            case FILAMENT_CHANGED -> handleFilamentChanged();

        }

    }

    private void handlePrinterOnline() {
        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/printer/online",
                        payload("printer.online")
                ));
    }

    private void handlePrinterOffline() {
        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/printer/offline",
                        payload("printer.offline")
                ));
    }

    private void handlePrintStarted(PrinterEvent event) {
        log.info("Print started: {}", event.getFileName());
        lastLogProgressMilestone = -1;
        lastLogLayerMilestone = -1;

        String sessionId = UUID.randomUUID().toString();
        printerStateService.getState().setSessionId(sessionId);
        cameraSnapshotStore.setCurrentSessionId(sessionId);
        modelMetadataWorkflowService.setMetadataLoaded(false);
        printerStateService.getState().setProgress(0);

        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/started",
                        payload("print.started", sessionId)
                ));

        CompletableFuture.runAsync(() -> {
            try {
                // Small delay before model acquisition.
                // Some printers may reject FTPS access immediately after PRINT_STARTED.
                Thread.sleep(1000);

                modelMetadataWorkflowService.requestMetadata();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Metadata download interrupted", e);
            } catch (Exception e) {
                log.error("Cannot download model!", e);
            }

        });
    }

    private void handlePrintRunning() {
        log.info("Print running");
        PrinterState state = printerStateService.getState();

        if (state.isPrinting()) {

            mqttMessagePublisher.publish(
                    "edolcore/print/running",
                    payload("print.running", state.getSessionId())
            );

        }
    }

    private void handlePrintPaused() {
        log.info("Print paused");
        PrinterState state = printerStateService.getState();

        mqttMessagePublisher.publish(
                "edolcore/print/paused",
                payload("print.paused", state.getSessionId())
        );
    }

    private void handlePrintFinished(String topic, String eventName) {
        PrinterState state = printerStateService.getState();
        state.setPrinting(false);

        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        topic,
                        payload(eventName, state.getSessionId())
                ));

        agentCommandGateway.disableSnapshotScheduler();

        generateTimelapse(state);
    }

    private void handlePrintError(PrinterEvent event) {
        Integer errorCode = event.getErrorCode();
        log.error("❌ Printer error code: {}", errorCode);

        PrinterState state = printerStateService.getState();
        state.setError(new PrinterError(
                errorCode,
                ErrorCodes.errorMap.get(errorCode)
        ));

        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/error",
                        payload(
                                "print.error",
                                ERROR_CODE_KEY, errorCode,
                                ERROR_MESSAGE_KEY, ErrorCodes.errorMap.get(errorCode)
                        )
                ));
    }

    private void handleLayerChanged(PrinterEvent event) {
        int layer = event.getLayer();
        if (isLayerLogMilestone(layer)) {
            log.info("Layer: {}", event.getLayer());
        }
    }

    private void handleProgressChanged(PrinterEvent event) {
        int progress = event.getProgress();

        PrinterState state = printerStateService.getState();

        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/progress",
                        payload("print.progress.changed", state.getSessionId())
                ));

        if (isProgressLogMilestone(progress)) {
            log.info("Progress: {}%", progress);
        }
    }


    private void handleAmsStatusChanged() {
        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/ams",
                        payload("ams.status.changed")
                ));

        log.info("AMS status changed");
    }

    private void handleAmdSlotChanged() {
        PrinterState state = printerStateService.getState();

        CompletableFuture.runAsync(() ->
                mqttMessagePublisher.publish(
                        "edolcore/print/ams",
                        payload(
                                "ams.slot.changed",
                                PREV_SLOT_KEY, state.getAms().getPreviousSlot(),
                                CURR_SLOT_KEY, state.getAms().getActiveSlot()
                        )
                ));

        log.info("AMS slot changed: {} -> {}",
                state.getAms().getPreviousSlot(),
                state.getAms().getActiveSlot());
    }

    private static void handleFilamentChanged() {
        log.info("AMS Filament changed");
    }


    private void generateTimelapse(PrinterState state) {
        String sessionId = state.getSessionId();

        cameraSnapshotStore.setCurrentSessionId("default");

        CompletableFuture.runAsync(() -> {
            try {
                File video = timelapseService.generate(sessionId);
                if (video != null) {

                    CompletableFuture.runAsync(() ->
                            mqttMessagePublisher.publish(
                                    "edolcore/print/timelapse",
                                    payload("print.timelapse", PATH_KEY, video.getAbsolutePath())
                            ));

                }
            } catch (Exception e) {
                log.error("Timelapse generation failed", e);
            }
        });
    }

    private boolean isProgressLogMilestone(int progress) {
        int milestone = progress / PROGRESS_LOG_STEP;

        if (milestone > lastLogProgressMilestone && milestone > 0) {
            lastLogProgressMilestone = milestone;
            return true;
        }

        return false;
    }

    private boolean isLayerLogMilestone(int layer) {
        int milestone = layer / LAYER_LOG_STEP;

        if (milestone > lastLogLayerMilestone && milestone > 0) {
            lastLogLayerMilestone = milestone;
            return true;
        }

        return false;
    }

    private Map<String, Object> payload(String eventName) {
        return Map.of(EVENT_KEY, eventName);
    }

    private Map<String, Object> payload(String eventName, String sessionId) {
        return Map.of(
                EVENT_KEY, eventName,
                SESSION_ID_KEY, sessionId
        );
    }

    private Map<String, Object> payload(String eventName, String key1, Object value1) {
        return Map.of(
                EVENT_KEY, eventName,
                key1, value1
        );
    }

    private Map<String, Object> payload(String eventName, String key1, Object value1, String key2, Object value2) {
        return Map.of(
                EVENT_KEY, eventName,
                key1, value1,
                key2, value2
        );
    }


}
