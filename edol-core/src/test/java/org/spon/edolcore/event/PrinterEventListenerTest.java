package org.spon.edolcore.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.MqttEventPublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.camera.CameraBackgroundService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.TimelapseService;
import org.spon.edolcore.service.printmetadata.ModelService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrinterEventListener")
class PrinterEventListenerTest {

    @Mock
    private ModelService modelService;

    @Mock
    private PrinterStateService printerStateService;

    @Mock
    private CameraSnapshotStore cameraSnapshotStore;

    @Mock
    private CameraBackgroundService cameraBackgroundService;

    @Mock
    private TimelapseService timelapseService;

    @Mock
    private MqttEventPublisher mqttEventPublisher;

    private PrinterEventListener listener;

    private final PrinterState printerState = new PrinterState();

    @BeforeEach
    void setUp() {
        printerState.setSessionId("test-session");
        when(printerStateService.getState()).thenReturn(printerState);
        listener = new PrinterEventListener(
                modelService, printerStateService, cameraSnapshotStore,
                cameraBackgroundService, timelapseService, mqttEventPublisher
        );
    }

    @Nested
    @DisplayName("PRINTER_ONLINE")
    class PrinterOnline {

        @Test
        @DisplayName("clears error and publishes printer.online")
        void publishesOnline() {
            printerState.setError(new org.spon.edol.model.PrinterError(1, "error"));

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINTER_ONLINE, null, null, null, null));

            assertThat(printerState.getError()).isNull();
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/printer/online"), any());
        }
    }

    @Nested
    @DisplayName("PRINTER_OFFLINE")
    class PrinterOffline {

        @Test
        @DisplayName("publishes printer.offline")
        void publishesOffline() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINTER_OFFLINE, null, null, null, null));

            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/printer/offline"), any());
        }
    }

    @Nested
    @DisplayName("PRINT_STARTED")
    class PrintStarted {

        @Test
        @DisplayName("sets sessionId, resets progress and metadata, starts camera")
        void startsPrint() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_STARTED, "test.gcode", 0, 0, null));

            assertThat(printerState.getSessionId()).isNotNull();
            assertThat(printerState.getProgress()).isZero();
            verify(cameraSnapshotStore).setCurrentSessionId(anyString());
            verify(cameraBackgroundService).capture();
            verify(modelService).setMetadataLoaded(false);
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/started"), any());
        }

        @Test
        @DisplayName("resets milestone counters")
        void resetsMilestones() {
            ReflectionTestUtils.setField(listener, "lastLogProgressMilestone", 10);
            ReflectionTestUtils.setField(listener, "lastLogLayerMilestone", 5);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_STARTED, "test.gcode", 0, 0, null));

            assertThat(ReflectionTestUtils.getField(listener, "lastLogProgressMilestone")).isEqualTo(-1);
            assertThat(ReflectionTestUtils.getField(listener, "lastLogLayerMilestone")).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("PRINT_RUNNING")
    class PrintRunning {

        @Test
        @DisplayName("publishes print.running when printing is true")
        void publishesWhenPrinting() {
            printerState.setPrinting(true);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_RUNNING, null, null, null, null));

            verify(mqttEventPublisher).publish(eq("edolcore/print/running"), any());
        }

        @Test
        @DisplayName("does nothing when printing is false")
        void doesNothingWhenNotPrinting() {
            printerState.setPrinting(false);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_RUNNING, null, null, null, null));

            verify(mqttEventPublisher, never()).publish(anyString(), any());
        }
    }

    @Nested
    @DisplayName("PRINT_PAUSED")
    class PrintPaused {

        @Test
        @DisplayName("publishes print.paused event")
        void publishesPaused() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_PAUSED, null, null, null, null));

            verify(mqttEventPublisher).publish(eq("edolcore/print/paused"), any());
        }
    }

    @Nested
    @DisplayName("PRINT_FINISHED")
    class PrintFinished {

        @Test
        @DisplayName("sets printing to false, publishes print.finished, generates timelapse")
        void finishesPrint() throws Exception {
            when(timelapseService.generate(any())).thenReturn(null);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_FINISHED, null, null, null, null));

            assertThat(printerState.isPrinting()).isFalse();
            verify(cameraSnapshotStore).setCurrentSessionId("default");
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/finished"), any());
        }
    }

    @Nested
    @DisplayName("PRINT_FAILED")
    class PrintFailed {

        @Test
        @DisplayName("sets printing to false, publishes print.failed, generates timelapse")
        void failsPrint() throws Exception {
            when(timelapseService.generate(any())).thenReturn(null);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_FAILED, null, null, null, null));

            assertThat(printerState.isPrinting()).isFalse();
            verify(cameraSnapshotStore).setCurrentSessionId("default");
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/failed"), any());
        }
    }

    @Nested
    @DisplayName("PRINT_ERROR")
    class PrintError {

        @Test
        @DisplayName("sets error on state and publishes print.error with code")
        void publishesError() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PRINT_ERROR, null, null, null, 50348044));

            verify(printerStateService, times(2)).getState();
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/error"), any());
        }
    }

    @Nested
    @DisplayName("PROGRESS_CHANGED")
    class ProgressChanged {

        @Test
        @DisplayName("publishes print.progress.changed via MQTT")
        void publishesProgress() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.PROGRESS_CHANGED, null, null, 50, null));

            verify(mqttEventPublisher).publish(eq("edolcore/print/progress"), any());
        }
    }

    @Nested
    @DisplayName("AMS_STATUS_CHANGED")
    class AmsStatusChanged {

        @Test
        @DisplayName("publishes ams.status.changed via MQTT")
        void publishesAmsStatus() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.AMS_STATUS_CHANGED, null, null, null, null));

            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/ams"), any());
        }
    }

    @Nested
    @DisplayName("AMS_SLOT_CHANGED")
    class AmsSlotChanged {

        @Test
        @DisplayName("publishes ams.slot.changed with previous and current slot")
        void publishesSlotChanged() {
            printerState.getAms().setPreviousSlot(1);
            printerState.getAms().setActiveSlot(2);

            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.AMS_SLOT_CHANGED, null, null, null, null));

            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/print/ams"), any());
        }
    }

    @Nested
    @DisplayName("FILAMENT_CHANGED")
    class FilamentChanged {

        @Test
        @DisplayName("handles filament changed event")
        void handlesFilamentChanged() {
            listener.handlePrinterEvent(new PrinterEvent(PrinterEventType.FILAMENT_CHANGED, null, null, null, null));

            verifyNoInteractions(mqttEventPublisher);
        }
    }
}