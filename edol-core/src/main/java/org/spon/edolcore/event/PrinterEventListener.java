package org.spon.edolcore.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ErrorCodes;
import org.spon.edol.model.PrinterError;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.MqttEventPublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.camera.CameraBackgroundService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.TimelapseService;
import org.spon.edolcore.service.printmetadata.ModelService;
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

    private final ModelService modelService;
    private final PrinterStateService printerStateService;
    private final CameraSnapshotStore cameraSnapshotStore;
    private final CameraBackgroundService cameraBackgroundService;
    private final TimelapseService timelapseService;
    private final MqttEventPublisher mqttEventPublisher;

    private int lastLogProgressMilestone = -1;
    private int lastLogLayerMilestone = -1;

    @EventListener
    public void handlePrinterEvent(PrinterEvent event) {

        log.info("PRINTER EVENT: {}", event.getType());

        printerStateService.getState().setError(null);

        switch (event.getType()) {

            case PRINTER_ONLINE -> {
                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/printer/online",
                                Map.of(
                                        "event", "printer.online"
                                )
                        ));
            }

            case PRINTER_OFFLINE -> {
                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/printer/offline",
                                Map.of(
                                        "event", "printer.offline"
                                )
                        ));
            }

            case PRINT_STARTED -> {
                log.info("Print started: {}", event.getFileName());
                lastLogProgressMilestone = -1;
                lastLogLayerMilestone = -1;

                String sessionId = UUID.randomUUID().toString();
                printerStateService.getState().setSessionId(sessionId);
                cameraSnapshotStore.setCurrentSessionId(sessionId);
                cameraBackgroundService.capture();  // Refresh image
                modelService.setMetadataLoaded(false);
                printerStateService.getState().setProgress(0);

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/started",
                                Map.of(
                                        "event", "print.started",
                                        "sessionId", sessionId
                                )
                        ));

                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(10000);

                        modelService.fetchMetadata();

                        Thread.sleep(20000);

                        mqttEventPublisher.publish(
                                "edolcore/print/metadata",
                                Map.of(
                                        "event", "print.metadata.loaded",
                                        "sessionId", sessionId
                                )
                        );

                        printerStateService.getState().setPrinting(true);

                    } catch (Exception e) {
                        log.error("Cannot download model!", e);
                    }

                });
            }

            case PRINT_RUNNING -> {
                log.info("Print running");
                PrinterState state = printerStateService.getState();

                if (state.isPrinting()) {

                    mqttEventPublisher.publish(
                            "edolcore/print/running",
                            Map.of(
                                    "event", "print.running",
                                    "sessionId", state.getSessionId()
                            )
                    );

                }
            }

            case PRINT_PAUSED -> {
                log.info("Print paused");
                PrinterState state = printerStateService.getState();

                mqttEventPublisher.publish(
                        "edolcore/print/paused",
                        Map.of(
                                "event", "print.paused",
                                "sessionId", state.getSessionId()
                        )
                );

            }

            case PRINT_FINISHED -> {
                log.info("Print finished");

                PrinterState state = printerStateService.getState();
                state.setPrinting(false);

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/finished",
                                Map.of(
                                        "event", "print.finished",
                                        "sessionId", state.getSessionId()
                                )
                        ));

                generateTimelapse(state);

            }

            case PRINT_FAILED -> {
                log.info("Print failed");

                PrinterState state = printerStateService.getState();
                state.setPrinting(false);

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/failed",
                                Map.of(
                                        "event", "print.failed",
                                        "sessionId", state.getSessionId()
                                )
                        ));

                generateTimelapse(state);

            }

            case PRINT_ERROR -> {
                Integer errorCode = event.getErrorCode();
                log.error("❌ Printer error code: {}", errorCode);

                PrinterState state = printerStateService.getState();
                state.setError(new PrinterError(
                        errorCode,
                        ErrorCodes.errorMap.get(errorCode)
                ));

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/error",
                                Map.of(
                                        "event", "print.error",
                                        "error-code", errorCode,
                                        "error-message", ErrorCodes.errorMap.get(errorCode)
                                )
                        ));

            }

            case LAYER_CHANGED -> {
                int layer = event.getLayer();
                if (isLayerLogMilestone(layer)) {
                    log.info("Layer: {}", event.getLayer());
                }
            }

            case PROGRESS_CHANGED -> {
                int progress = event.getProgress();

                PrinterState state = printerStateService.getState();

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/progress",
                                Map.of(
                                        "event", "print.progress.changed",
                                        "sessionId", state.getSessionId()
                                )
                        ));

                if (isProgressLogMilestone(progress)) {
                    log.info("Progress: {}%", progress);
                }

            }

            case AMS_STATUS_CHANGED -> {
                CompletableFuture.runAsync(() -> {
                    PrinterState state = printerStateService.getState();

                    mqttEventPublisher.publish(
                            "edolcore/print/ams",
                            Map.of(
                                    "event", "ams.status.changed"
                                    //"ams", state.getAms()
                            )
                    );
                });

                log.info("AMS status changed");
            }

            case AMS_SLOT_CHANGED -> {
                PrinterState state = printerStateService.getState();

                CompletableFuture.runAsync(() ->
                        mqttEventPublisher.publish(
                                "edolcore/print/ams",
                                Map.of(
                                        "event", "ams.slot.changed",
                                        "prev_slot", state.getAms().getPreviousSlot(),
                                        "curr_slot", state.getAms().getActiveSlot()
                                )
                        ));

                log.info("AMS slot changed: {} -> {}",
                        state.getAms().getPreviousSlot(),
                        state.getAms().getActiveSlot());
            }

            case FILAMENT_CHANGED -> log.info("AMS Filament changed");

        }

    }

    private void generateTimelapse(PrinterState state) {
        String sessionId = state.getSessionId();

        cameraSnapshotStore.setCurrentSessionId("default");

        CompletableFuture.runAsync(() -> {
            try {
                File video = timelapseService.generate(sessionId);
                if (video != null) {

                    CompletableFuture.runAsync(() ->
                            mqttEventPublisher.publish(
                                    "edolcore/print/timelapse",
                                    Map.of(
                                            "event", "print.timelapse",
                                            "path", video.getAbsolutePath()

                                    )
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


}
