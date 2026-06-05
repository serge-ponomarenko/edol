package org.spon.edolcore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.AmsEvent;
import org.spon.edolcore.event.PrinterEvent;
import org.spon.edolcore.event.PrinterEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrinterStateService")
class PrinterStateServiceTest {

    @Mock
    private ApplicationEventPublisher events;

    private PrinterStateService service;

    private final ObjectMapper mapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<PrinterEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<AmsEvent> amsEventCaptor;

    @BeforeEach
    void setUp() {
        service = new PrinterStateService(events);
    }

    @Nested
    @DisplayName("update - gcode_state")
    class UpdateGcodeState {

        @Test
        @DisplayName("IDLE to PREPARE fires PRINT_STARTED event")
        void idleToPrepare() throws Exception {
            JsonNode print = mapper.readTree("{\"gcode_state\": \"PREPARE\"}");

            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_STARTED);
            assertThat(service.getState().getGcodeState()).isEqualTo("PREPARE");
        }

        @Test
        @DisplayName("PREPARE to RUNNING fires PRINT_RUNNING event")
        void prepareToRunning() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "PREPARE");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"RUNNING\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_RUNNING);
        }

        @Test
        @DisplayName("RUNNING to FINISH fires PRINT_FINISHED event")
        void runningToFinish() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "RUNNING");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"FINISH\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_FINISHED);
        }

        @Test
        @DisplayName("RUNNING to FAILED fires PRINT_FAILED event")
        void runningToFailed() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "RUNNING");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"FAILED\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_FAILED);
        }

        @Test
        @DisplayName("RUNNING to PAUSE fires PRINT_PAUSED event")
        void runningToPause() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "RUNNING");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"PAUSE\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_PAUSED);
        }

        @Test
        @DisplayName("does not fire state event when gcodeState unchanged")
        void noEventWhenStateUnchanged() throws Exception {
            // First update triggers PRINT_STARTED: IDLE -> PREPARE
            JsonNode print1 = mapper.readTree("{\"gcode_state\": \"PREPARE\"}");
            service.update(print1);
            verify(events).publishEvent(any(PrinterEvent.class));

            // Second update with same state should not trigger another event
            reset(events);
            JsonNode print2 = mapper.readTree("{\"gcode_state\": \"PREPARE\"}");
            service.update(print2);

            verify(events, never()).publishEvent(any(PrinterEvent.class));
        }

        @Test
        @DisplayName("FINISH to PREPARE fires PRINT_STARTED event")
        void finishToPrepare() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "FINISH");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"PREPARE\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_STARTED);
        }

        @Test
        @DisplayName("PAUSE to RUNNING fires PRINT_RUNNING event")
        void pauseToRunning() throws Exception {
            ReflectionTestUtils.setField(service, "lastState", "PAUSE");

            JsonNode print = mapper.readTree("{\"gcode_state\": \"RUNNING\"}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_RUNNING);
        }
    }

    @Nested
    @DisplayName("update - progress")
    class UpdateProgress {

        @Test
        @DisplayName("updates progress and fires PROGRESS_CHANGED event")
        void progressChanged() throws Exception {
            JsonNode print = mapper.readTree("{\"mc_percent\": 50}");
            service.update(print);

            assertThat(service.getState().getProgress()).isEqualTo(50);
            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PROGRESS_CHANGED);
        }

        @Test
        @DisplayName("does not fire progress event when progress unchanged")
        void noEventWhenProgressUnchanged() throws Exception {
            JsonNode print = mapper.readTree("{\"mc_percent\": 50}");
            service.update(print);
            service.update(print);

            verify(events, times(1)).publishEvent(eventCaptor.capture());
        }
    }

    @Nested
    @DisplayName("update - layer")
    class UpdateLayer {

        @Test
        @DisplayName("updates layer and fires LAYER_CHANGED event")
        void layerChanged() throws Exception {
            JsonNode print = mapper.readTree("{\"layer_num\": 5}");
            service.update(print);

            assertThat(service.getState().getLayer()).isEqualTo(5);
            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.LAYER_CHANGED);
            assertThat(eventCaptor.getValue().getLayer()).isEqualTo(5);
        }

        @Test
        @DisplayName("does not fire layer event when layer unchanged")
        void noEventWhenLayerUnchanged() throws Exception {
            JsonNode print = mapper.readTree("{\"layer_num\": 5}");
            service.update(print);
            service.update(print);

            verify(events, times(1)).publishEvent(any(PrinterEvent.class));
        }
    }

    @Nested
    @DisplayName("update - error")
    class UpdateError {

        @Test
        @DisplayName("detects new non-zero error and fires PRINT_ERROR event")
        void newError() throws Exception {
            JsonNode print = mapper.readTree("{\"print_error\": 50348044}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINT_ERROR);
            assertThat(eventCaptor.getValue().getErrorCode()).isEqualTo(50348044);
        }

        @Test
        @DisplayName("ignores zero error codes")
        void ignoresZeroError() throws Exception {
            JsonNode print = mapper.readTree("{\"print_error\": 0}");
            service.update(print);

            verify(events, never()).publishEvent(any(PrinterEvent.class));
        }

        @Test
        @DisplayName("ignores repeated same error")
        void ignoresSameError() throws Exception {
            JsonNode print = mapper.readTree("{\"print_error\": 50348044}");
            service.update(print);
            service.update(print);

            verify(events, times(1)).publishEvent(any(PrinterEvent.class));
        }
    }

    @Nested
    @DisplayName("update - AMS")
    class UpdateAms {

        @Test
        @DisplayName("detects AMS status change and fires event")
        void amsStatusChanged() throws Exception {
            JsonNode print = mapper.readTree("{\"ams_status\": 1}");
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.AMS_STATUS_CHANGED);
        }

        @Test
        @DisplayName("parses AMS mapping")
        void parsesAmsMapping() throws Exception {
            JsonNode print = mapper.readTree("{\"ams_mapping\": [0, 1, -1, -1]}");
            service.update(print);

            assertThat(service.getState().getAmsMapping()).containsExactly(0, 1, -1, -1);
        }

        @Test
        @DisplayName("detects external spool when tray_now is 254")
        void externalSpoolUsed() throws Exception {
            JsonNode print = mapper.readTree("{\"ams\": {\"tray_now\": 254}}");
            service.update(print);

            assertThat(service.getState().isExternalSpoolUsed()).isTrue();
        }

        @Test
        @DisplayName("parses AMS slots from tray array")
        void parsesAmsSlots() throws Exception {
            String json = """
                    {
                        "ams": {
                            "ams": [{
                                "id": 0,
                                "temp": 25.5,
                                "humidity": 45,
                                "humidity_raw": 450,
                                "tray": [
                                    {"id": 0, "tray_type": "PLA", "tray_sub_brands": "Generic", "tray_info_idx": "GFL99", "tray_color": "FF0000", "remain": 80},
                                    {"id": 1, "tray_type": "", "tray_sub_brands": "", "tray_info_idx": "", "tray_color": "FFFFFF", "remain": 0}
                                ]
                            }],
                            "tray_now": 0
                        }
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            assertThat(service.getState().getAms()).isNotNull();
            assertThat(service.getState().getAms().getSlots()).hasSize(2);
            assertThat(service.getState().getAms().getTemperature()).isEqualTo(25.5);
            assertThat(service.getState().getAms().getHumidity()).isEqualTo(45);
            assertThat(service.getState().getAms().getActiveSlot()).isZero();

            AmsSlot slot0 = service.getState().getAms().getSlots().getFirst();
            assertThat(slot0.getId()).isZero();
            assertThat(slot0.getFilamentType()).isEqualTo("PLA");
            assertThat(slot0.getFilamentBrand()).isEqualTo("Generic");
            assertThat(slot0.getColor()).isEqualTo("#FF0000");
            assertThat(slot0.getRemaining()).isEqualTo(80);
        }

        @Test
        @DisplayName("detects slot loaded event when slot was empty now filled")
        void detectsSlotLoaded() throws Exception {
            AmsSlot emptySlot = new AmsSlot();
            emptySlot.setId(0);
            emptySlot.setFilamentType("");
            emptySlot.setColor("");
            emptySlot.setRemaining(0);
            List<AmsSlot> previous = new ArrayList<>();
            previous.add(emptySlot);

            ReflectionTestUtils.setField(service, "previousAmsSlots", previous);

            String json = """
                    {
                        "ams": {
                            "ams": [{
                                "id": 0,
                                "tray": [
                                    {"id": 0, "tray_type": "PLA", "tray_sub_brands": "Generic", "tray_info_idx": "GFL99", "tray_color": "FF0000", "remain": 80}
                                ]
                            }]
                        }
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            verify(events).publishEvent(amsEventCaptor.capture());
            AmsEvent amsEvent = amsEventCaptor.getValue();
            assertThat(amsEvent.getType().name()).contains("LOADED");
        }

        @Test
        @DisplayName("detects slot unloaded event when slot was filled now empty")
        void detectsSlotUnloaded() throws Exception {
            AmsSlot filledSlot = new AmsSlot();
            filledSlot.setId(0);
            filledSlot.setFilamentType("PLA");
            filledSlot.setColor("#FF0000");
            filledSlot.setRemaining(80);
            filledSlot.setFilamentBrandIndex("GFL99");
            filledSlot.setFilamentBrand("Generic");
            List<AmsSlot> previous = new ArrayList<>();
            previous.add(filledSlot);

            ReflectionTestUtils.setField(service, "previousAmsSlots", previous);

            String json = """
                    {
                        "ams": {
                            "ams": [{
                                "id": 0,
                                "tray": [
                                    {"id": 0, "remain": 0}
                                ]
                            }]
                        }
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            verify(events).publishEvent(amsEventCaptor.capture());
            AmsEvent amsEvent = amsEventCaptor.getValue();
            assertThat(amsEvent.getType().name()).contains("UNLOADED");
        }

        @Test
        @DisplayName("fires AMS_SLOT_CHANGED when active slot changes")
        void activeSlotChanged() throws Exception {
            String json = """
                    {
                        "ams": {
                            "tray_now": 2
                        }
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.AMS_SLOT_CHANGED);
        }
    }

    @Nested
    @DisplayName("update - extTray")
    class UpdateExtTray {

        @Test
        @DisplayName("parses external tray filament data")
        void parsesExtTray() throws Exception {
            String json = """
                    {
                        "vt_tray": {
                            "tray_type": "PLA",
                            "tray_sub_brands": "Bambu",
                            "tray_info_idx": "GFL99",
                            "tray_color": "FF00FF",
                            "remain": 80
                        }
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            assertThat(service.getState().getExtTray()).isNotNull();
            assertThat(service.getState().getExtTray().getFilamentType()).isEqualTo("PLA");
            assertThat(service.getState().getExtTray().getFilamentBrand()).isEqualTo("Bambu");
            assertThat(service.getState().getExtTray().getColor()).isEqualTo("#FF00FF");
            assertThat(service.getState().getExtTray().getRemaining()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("update - metadata fields")
    class UpdateMetadata {

        @Test
        @DisplayName("parses temperature fields")
        void parsesTemperatures() throws Exception {
            String json = """
                    {
                        "nozzle_temper": 210.5,
                        "nozzle_target_temper": 220.0,
                        "bed_temper": 65.0,
                        "bed_target_temper": 65.0
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            assertThat(service.getState().getNozzleTemp()).isEqualTo(210.5);
            assertThat(service.getState().getNozzleTargetTemp()).isEqualTo(220.0);
            assertThat(service.getState().getBedTemp()).isEqualTo(65.0);
            assertThat(service.getState().getBedTargetTemp()).isEqualTo(65.0);
        }

        @Test
        @DisplayName("parses remaining time")
        void parsesRemainingTime() throws Exception {
            JsonNode print = mapper.readTree("{\"mc_remaining_time\": 45}");
            service.update(print);

            assertThat(service.getState().getRemainingTime()).isEqualTo(45);
        }

        @Test
        @DisplayName("parses gcode file and subtask name")
        void parsesFileAndTask() throws Exception {
            String json = """
                    {
                        "gcode_file": "test.gcode",
                        "subtask_name": "My Print"
                    }
                    """;
            JsonNode print = mapper.readTree(json);
            service.update(print);

            assertThat(service.getState().getCurrentFile()).isEqualTo("test.gcode");
            assertThat(service.getState().getCurrentTask()).isEqualTo("My Print");
        }

        @Test
        @DisplayName("parses total layer number")
        void parsesTotalLayers() throws Exception {
            JsonNode print = mapper.readTree("{\"total_layer_num\": 160}");
            service.update(print);

            assertThat(service.getState().getTotalLayers()).isEqualTo(160);
        }

        @Test
        @DisplayName("parses wifi signal")
        void parsesWifiSignal() throws Exception {
            JsonNode print = mapper.readTree("{\"wifi_signal\": \"-45dBm\"}");
            service.update(print);

            assertThat(service.getState().getWifiSignal()).isEqualTo("-45dBm");
        }

        @Test
        @DisplayName("parses speed")
        void parsesSpeed() throws Exception {
            JsonNode print = mapper.readTree("{\"spd_mag\": 100}");
            service.update(print);

            assertThat(service.getState().getSpeed()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("update - combined JSON")
    class UpdateCombined {

        @Test
        @DisplayName("processes multiple fields in a single update")
        void processesMultipleFields() throws Exception {
            String json = """
                    {
                        "gcode_state": "RUNNING",
                        "mc_percent": 75,
                        "layer_num": 120,
                        "total_layer_num": 160,
                        "mc_remaining_time": 30,
                        "gcode_file": "model.gcode",
                        "subtask_name": "Test Print",
                        "nozzle_temper": 215.0,
                        "bed_temper": 60.0,
                        "wifi_signal": "-50dBm",
                        "spd_mag": 50,
                        "ams_mapping": [0, 1, -1, -1]
                    }
                    """;

            ReflectionTestUtils.setField(service, "lastState", "PREPARE");
            JsonNode print = mapper.readTree(json);
            service.update(print);

            PrinterState state = service.getState();
            assertThat(state.getGcodeState()).isEqualTo("RUNNING");
            assertThat(state.getProgress()).isEqualTo(75);
            assertThat(state.getLayer()).isEqualTo(120);
            assertThat(state.getTotalLayers()).isEqualTo(160);
            assertThat(state.getRemainingTime()).isEqualTo(30);
            assertThat(state.getCurrentFile()).isEqualTo("model.gcode");
            assertThat(state.getCurrentTask()).isEqualTo("Test Print");
            assertThat(state.getNozzleTemp()).isEqualTo(215.0);
            assertThat(state.getBedTemp()).isEqualTo(60.0);
            assertThat(state.getWifiSignal()).isEqualTo("-50dBm");
            assertThat(state.getSpeed()).isEqualTo(50);
            assertThat(state.getAmsMapping()).containsExactly(0, 1, -1, -1);

            verify(events, atLeastOnce()).publishEvent(any(PrinterEvent.class));
        }
    }

    @Nested
    @DisplayName("publish")
    class PublishMethod {

        @Test
        @DisplayName("publishes an event of the given type")
        void publishesEvent() {
            service.publish(PrinterEventType.PRINTER_ONLINE);

            verify(events).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo(PrinterEventType.PRINTER_ONLINE);
        }
    }

    @Nested
    @DisplayName("getState")
    class GetState {

        @Test
        @DisplayName("returns the current printer state")
        void returnsState() {
            PrinterState state = service.getState();
            assertThat(state).isNotNull();
            assertThat(state.getPrinterId()).isEqualTo(1);
        }
    }
}