package org.spon.edolcore.service;



import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.*;
import org.spon.edolcore.event.AmsEvent;
import org.spon.edolcore.event.AmsEventType;
import org.spon.edolcore.event.PrinterEvent;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.event.printer.PrinterStateUpdatedEvent;
import org.spon.edolcore.service.print.recovery.StartupSynchronizationService;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeContextProvider;
import org.spon.edolcore.service.printer.runtime.PrinterStateRuntime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
@Slf4j
@Service
public class PrinterStateService {

    private static final String FIELD_COMMAND_NAME = "command";
    private static final String FIELD_GCODE_STATE = "gcode_state";
    private static final String FIELD_MC_PERCENT = "mc_percent";
    private static final String FIELD_LAYER_NUM = "layer_num";
    private static final String FIELD_PRINT_ERROR = "print_error";
    private static final String FIELD_AMS_STATUS = "ams_status";
    private static final String FIELD_AMS = "ams";
    private static final String FIELD_AMS_MAPPING = "ams_mapping";
    private static final String FIELD_VT_TRAY = "vt_tray";
    private static final String FIELD_TOTAL_LAYER_NUM = "total_layer_num";
    private static final String FIELD_MC_REMAINING_TIME = "mc_remaining_time";
    private static final String FIELD_NOZZLE_TEMPER = "nozzle_temper";
    private static final String FIELD_NOZZLE_TARGET_TEMPER = "nozzle_target_temper";
    private static final String FIELD_BED_TEMPER = "bed_temper";
    private static final String FIELD_BED_TARGET_TEMPER = "bed_target_temper";
    private static final String FIELD_SUBTASK_NAME = "subtask_name";
    private static final String FIELD_FILE_NAME = "file";
    private static final String FIELD_WIFI_SIGNAL = "wifi_signal";
    private static final String FIELD_SPD_MAG = "spd_mag";
    private static final String FIELD_TRAY_NOW = "tray_now";
    private static final String FIELD_TRAY_TAR = "tray_tar";
    private static final String FIELD_TRAY_TYPE = "tray_type";
    private static final String FIELD_TRAY_SUB_BRANDS = "tray_sub_brands";
    private static final String FIELD_TRAY_INFO_IDX = "tray_info_idx";
    private static final String FIELD_TRAY_COLOR = "tray_color";
    private static final String FIELD_REMAIN = "remain";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TEMP = "temp";
    private static final String FIELD_HUMIDITY = "humidity";
    private static final String FIELD_HUMIDITY_RAW = "humidity_raw";
    private static final String FIELD_AMS_TRAY = "tray";
    private static final String FIELD_AMS_LIST = "ams";

    private static final String PROJECT_FILE = "project_file";

    private static final String STATE_IDLE = "IDLE";
    private static final String STATE_FINISH = "FINISH";
    private static final String STATE_FAILED = "FAILED";
    private static final String STATE_PAUSE = "PAUSE";
    private static final String STATE_PREPARE = "PREPARE";
    private static final String STATE_RUNNING = "RUNNING";

    private final ApplicationEventPublisher events;
    private final StartupSynchronizationService startupSynchronizationService;
    private final PrinterRuntimeContextProvider runtimeContextProvider;
    private final LogContextFactory logContextFactory;


    private PrinterStateRuntime runtime(UUID printerId) {
        return runtimeContextProvider
                .getContext(printerId)
                .getPrinterStateRuntime();
    }

    public PrinterState getState(UUID printerId) {
        return runtime(printerId).getState();
    }

    public void update(
            UUID printerId,
            JsonNode print
    ) {
        synchronized (runtime(printerId)) {
            List<PrinterEvent> pendingEvents = new ArrayList<>();
            List<AmsEvent> pendingAmsEvents = new ArrayList<>();

            updateGcodeState(printerId, print, pendingEvents);
            updateProgress(printerId, print, pendingEvents);
            updateLayer(printerId, print, pendingEvents);
            updateError(printerId, print, pendingEvents);
            updateAmsStatus(printerId, print, pendingEvents);
            updateAms(printerId, print, pendingAmsEvents);
            updateAmsMapping(printerId, print);
            updateExtTray(printerId, print);
            updateAmsActiveSlot(printerId, print, pendingEvents);
            updateScalarFields(printerId, print);

            if (!startupSynchronizationService.isRecoverySynchronizationActive(printerId)) {
                pendingEvents.forEach(events::publishEvent);
                pendingAmsEvents.forEach(events::publishEvent);
            }

            events.publishEvent(new PrinterStateUpdatedEvent(printerId));
        }
    }

    private void updateAms(UUID printerId, JsonNode print, List<AmsEvent> pendingAmsEvents) {
        if (!print.has(FIELD_AMS))
            return;

        JsonNode amsNode = print.get(FIELD_AMS);
        JsonNode ams = getFirstAms(amsNode);
        if (ams == null)
            return;

        AmsState amsState = new AmsState();
        amsState.setAmsId(ams.get(FIELD_ID).asInt());
        applyAmsEnvironment(ams, amsState);

        List<AmsSlot> slots = buildAmsSlots(ams);
        detectAmsSlotChanges(printerId, slots, pendingAmsEvents);
        amsState.setSlots(slots);
        previousAmsSlots(printerId, slots);
        applyAmsActiveSlot(amsNode, amsState);

        getState(printerId).setAms(amsState);
    }

    private void updateExtTray(UUID printerId, JsonNode print) {
        if (!print.has(FIELD_VT_TRAY))
            return;

        JsonNode vtTrayNode = print.get(FIELD_VT_TRAY);
        ExtTray extTray = new ExtTray();

        applyTrayMetadata(vtTrayNode, extTray);

        getState(printerId).setExtTray(extTray);
    }

    private void detectStateEvents(UUID printerId, String oldState, String newState, List<PrinterEvent> pendingEvents) {
        if (oldState == null) {
            oldState = STATE_IDLE;
        }

        if (oldState.equals(newState))
            return;

        if (isPrintStartedTransition(oldState, newState)) {
            pendingEvents.add(createPrintEvent(printerId, PrinterEventType.PRINT_STARTED));
        }
        if (isPrintRunningTransition(oldState, newState)) {
            pendingEvents.add(createPrintEvent(printerId, PrinterEventType.PRINT_RUNNING));
        }
        if (isPrintPausedTransition(oldState, newState)) {
            pendingEvents.add(createPrintEvent(printerId, PrinterEventType.PRINT_PAUSED));
        }
        if (isPrintFinishedTransition(oldState, newState)) {
            pendingEvents.add(createPrintEvent(printerId, PrinterEventType.PRINT_FINISHED));
        }
        if (isPrintFailedTransition(oldState, newState)) {
            pendingEvents.add(createPrintEvent(printerId, PrinterEventType.PRINT_FAILED));
        }

    }

    private void updateGcodeState(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_GCODE_STATE))
            return;

        String newState = print.get(FIELD_GCODE_STATE).asText();

        if (newState.equals(lastState(printerId)))
            return;

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        getState(printerId).getSessionId()
                )
                .log(
                        "G-code new state: {}. Old state: {}", newState, lastState(printerId)
                );

        detectStateEvents(printerId, lastState(printerId), newState, pendingEvents);
        getState(printerId).setGcodeState(newState);
        lastState(printerId, newState);
    }

    private void updateProgress(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_MC_PERCENT))
            return;

        int progress = print.get(FIELD_MC_PERCENT).asInt();

        if (progress == lastProgress(printerId))
            return;

        getState(printerId).setProgress(progress);
        pendingEvents.add(
                createPrintEvent(
                        printerId,
                        PrinterEventType.PROGRESS_CHANGED
                )
        );
        lastProgress(printerId, progress);
    }

    private void updateLayer(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_LAYER_NUM))
            return;

        int layer = print.get(FIELD_LAYER_NUM).asInt();

        if (layer == lastLayer(printerId))
            return;

        getState(printerId).setLayer(layer);
        pendingEvents.add(
                createPrintEvent(
                        printerId,
                        PrinterEventType.LAYER_CHANGED
                )
        );
        lastLayer(printerId, layer);
    }

    private void updateError(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_PRINT_ERROR))
            return;

        int errorCode = print.get(FIELD_PRINT_ERROR).asInt();

        if (errorCode == 0 || errorCode == lastError(printerId))
            return;

        getState(printerId).setError(
                new PrinterError(
                        errorCode,
                        ErrorCodes.errorMap.get(errorCode)
                )
        );

        pendingEvents.add(
                createPrintEvent(
                        printerId,
                        PrinterEventType.PRINT_ERROR
                )
        );
        lastError(printerId, errorCode);
    }

    private void updateAmsStatus(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_AMS_STATUS))
            return;

        int amsStatus = print.get(FIELD_AMS_STATUS).asInt();

        if (amsStatus == lastAmsStatus(printerId))
            return;

        pendingEvents.add(
                createPrintEvent(
                        printerId,
                        PrinterEventType.AMS_STATUS_CHANGED
                )
        );
        lastAmsStatus(printerId, amsStatus);
    }

    private void updateAmsMapping(UUID printerId, JsonNode print) {
        if (!print.has(FIELD_AMS_MAPPING))
            return;

        JsonNode amsMapping = print.get(FIELD_AMS_MAPPING);
        List<Integer> amsMappingList = new ArrayList<>();
        for (JsonNode node : amsMapping) {
            amsMappingList.add(node.asInt());
        }
        getState(printerId).setAmsMapping(amsMappingList);
    }

    private void updateAmsActiveSlot(UUID printerId, JsonNode print, List<PrinterEvent> pendingEvents) {
        if (!print.has(FIELD_AMS))
            return;

        JsonNode amsNode = print.get(FIELD_AMS);
        if (!amsNode.has(FIELD_TRAY_NOW))
            return;

        int active = amsNode.get(FIELD_TRAY_NOW).asInt();

        getState(printerId).getAms().setActiveSlot(active);
        getState(printerId).setExternalSpoolUsed(active == 254);

        if (active == lastActiveSlot(printerId))
            return;

        pendingEvents.add(
                createPrintEvent(
                        printerId,
                        PrinterEventType.AMS_SLOT_CHANGED
                )
        );
        getState(printerId).getAms().setPreviousSlot(lastActiveSlot(printerId));
        lastActiveSlot(printerId, active);
    }

    private void updateScalarFields(UUID printerId, JsonNode print) {
        if (
                print.has(FIELD_COMMAND_NAME)
                        && print.get(FIELD_COMMAND_NAME).asText().equals(PROJECT_FILE)
                        && print.has(FIELD_FILE_NAME)
        ) {
            getState(printerId).setCurrentFile(print.get(FIELD_FILE_NAME).asText());
        }

        if (
                STATE_RUNNING.equals(getState(printerId).getGcodeState())
                        && (getState(printerId).getCurrentFile() == null || getState(printerId).getCurrentFile().isEmpty())
                        && print.has(FIELD_SUBTASK_NAME)
        ) {
            getState(printerId).setCurrentFile(print.get(FIELD_SUBTASK_NAME).asText() + ".gcode.3mf");
        }

        if (print.has(FIELD_SUBTASK_NAME)) {
            getState(printerId).setCurrentTask(print.get(FIELD_SUBTASK_NAME).asText());
        }

        if (print.has(FIELD_TOTAL_LAYER_NUM))
            getState(printerId).setTotalLayers(print.get(FIELD_TOTAL_LAYER_NUM).asInt());

        if (print.has(FIELD_MC_REMAINING_TIME))
            getState(printerId).setRemainingTime(print.get(FIELD_MC_REMAINING_TIME).asInt());

        if (print.has(FIELD_NOZZLE_TEMPER))
            getState(printerId).setNozzleTemp(print.get(FIELD_NOZZLE_TEMPER).asDouble());

        if (print.has(FIELD_NOZZLE_TARGET_TEMPER))
            getState(printerId).setNozzleTargetTemp(print.get(FIELD_NOZZLE_TARGET_TEMPER).asDouble());

        if (print.has(FIELD_BED_TEMPER))
            getState(printerId).setBedTemp(print.get(FIELD_BED_TEMPER).asDouble());

        if (print.has(FIELD_BED_TARGET_TEMPER))
            getState(printerId).setBedTargetTemp(print.get(FIELD_BED_TARGET_TEMPER).asDouble());

        if (print.has(FIELD_WIFI_SIGNAL))
            getState(printerId).setWifiSignal(print.get(FIELD_WIFI_SIGNAL).asText());

        if (print.has(FIELD_SPD_MAG))
            getState(printerId).setSpeed(print.get(FIELD_SPD_MAG).asInt());
    }

    private boolean isPrintStartedTransition(String oldState, String newState) {
        return isAnyOf(oldState, STATE_IDLE, STATE_FINISH, STATE_FAILED)
                && isAnyOf(newState, STATE_PREPARE, STATE_RUNNING);
    }

    private boolean isPrintRunningTransition(String oldState, String newState) {
        return isAnyOf(oldState, STATE_PREPARE, STATE_PAUSE) && STATE_RUNNING.equals(newState);
    }

    private boolean isPrintPausedTransition(String oldState, String newState) {
        return !STATE_PAUSE.equals(oldState) && STATE_PAUSE.equals(newState);
    }

    private boolean isPrintFinishedTransition(String oldState, String newState) {
        return !STATE_FINISH.equals(oldState) && STATE_FINISH.equals(newState);
    }

    private boolean isPrintFailedTransition(String oldState, String newState) {
        return !STATE_FAILED.equals(oldState) && STATE_FAILED.equals(newState);
    }

    private boolean isAnyOf(String value, String first, String second, String third) {
        return first.equals(value) || second.equals(value) || third.equals(value);
    }

    private boolean isAnyOf(String value, String first, String second) {
        return first.equals(value) || second.equals(value);
    }

    private JsonNode getFirstAms(JsonNode amsNode) {
        if (!amsNode.has(FIELD_AMS_LIST))
            return null;

        JsonNode amsArray = amsNode.get(FIELD_AMS_LIST);
        if (amsArray.isEmpty())
            return null;

        return amsArray.get(0);
    }

    private void applyAmsEnvironment(JsonNode ams, AmsState amsState) {
        if (ams.has(FIELD_TEMP))
            amsState.setTemperature(ams.get(FIELD_TEMP).asDouble());

        if (ams.has(FIELD_HUMIDITY))
            amsState.setHumidity(ams.get(FIELD_HUMIDITY).asInt());

        if (ams.has(FIELD_HUMIDITY_RAW))
            amsState.setHumidityRaw(ams.get(FIELD_HUMIDITY_RAW).asInt());
    }

    private List<AmsSlot> buildAmsSlots(JsonNode ams) {
        List<AmsSlot> slots = new ArrayList<>();
        JsonNode trays = ams.get(FIELD_AMS_TRAY);

        for (JsonNode tray : trays) {
            AmsSlot slot = new AmsSlot();
            slot.setId(tray.get(FIELD_ID).asInt());
            applyTrayMetadata(tray, slot);
            slots.add(slot);
        }

        return slots;
    }

    private void detectAmsSlotChanges(UUID printerId, List<AmsSlot> slots, List<AmsEvent> pendingAmsEvents) {
        if (previousAmsSlots(printerId).equals(slots))
            return;

        previousAmsSlots(printerId).forEach(previousSlot -> {
            int slotId = previousSlot.getId();
            AmsSlot currentSlot = slots.get(slotId);

            if (previousSlot.isEmpty() && !currentSlot.isEmpty()) {
                logContextFactory
                        .session(
                                log.atInfo(),
                                printerId,
                                getState(printerId).getSessionId()
                        )
                        .log(
                                "[AMS] New Spool has been loaded into slot {}", slotId
                        );
                pendingAmsEvents.add(new AmsEvent(printerId, AmsEventType.AMS_SLOT_LOADED, slotId));
            }

            if (!previousSlot.isEmpty() && currentSlot.isEmpty()) {
                logContextFactory
                        .session(
                                log.atInfo(),
                                printerId,
                                getState(printerId).getSessionId()
                        )
                        .log(
                                "[AMS] Spool has been unloaded from slot {}", slotId
                        );
                pendingAmsEvents.add(new AmsEvent(printerId, AmsEventType.AMS_SLOT_UNLOADED, slotId));
            }
        });
    }

    private void applyAmsActiveSlot(JsonNode amsNode, AmsState amsState) {
        if (amsNode.has(FIELD_TRAY_TAR))
            amsState.setActiveSlot(amsNode.get(FIELD_TRAY_TAR).asInt());

        if (amsNode.has(FIELD_TRAY_NOW))
            amsState.setActiveSlot(amsNode.get(FIELD_TRAY_NOW).asInt());
    }

    private void applyTrayMetadata(JsonNode trayNode, SpoolTray tray) {
        TrayMetadata metadata = extractTrayMetadata(trayNode);

        if (metadata.filamentType() != null)
            tray.setFilamentType(metadata.filamentType());

        if (metadata.filamentBrand() != null)
            tray.setFilamentBrand(metadata.filamentBrand());

        if (metadata.filamentBrandIndex() != null)
            tray.setFilamentBrandIndex(metadata.filamentBrandIndex());

        if (metadata.color() != null)
            tray.setColor(metadata.color());

        if (metadata.remaining() != null)
            tray.setRemaining(metadata.remaining());
    }

    private TrayMetadata extractTrayMetadata(JsonNode trayNode) {
        String filamentType = trayNode.has(FIELD_TRAY_TYPE) ? trayNode.get(FIELD_TRAY_TYPE).asText() : null;
        String filamentBrand = trayNode.has(FIELD_TRAY_SUB_BRANDS) ? trayNode.get(FIELD_TRAY_SUB_BRANDS).asText() : null;
        String filamentBrandIndex = trayNode.has(FIELD_TRAY_INFO_IDX) ? trayNode.get(FIELD_TRAY_INFO_IDX).asText() : null;
        String color = trayNode.has(FIELD_TRAY_COLOR) ? "#" + trayNode.get(FIELD_TRAY_COLOR).asText().substring(0, 6) : null;
        Integer remaining = trayNode.has(FIELD_REMAIN) ? trayNode.get(FIELD_REMAIN).asInt() : null;
        return new TrayMetadata(filamentType, filamentBrand, filamentBrandIndex, color, remaining);
    }

    private record TrayMetadata(
            String filamentType,
            String filamentBrand,
            String filamentBrandIndex,
            String color,
            Integer remaining
    ) {
    }

    private PrinterEvent createPrintEvent(UUID printerId, PrinterEventType type) {
        return new PrinterEvent(
                printerId,
                type
        );
    }

    public void publish(UUID printerId, PrinterEventType type) {
        events.publishEvent(createPrintEvent(printerId, type));
    }

    private String lastState(UUID printerId) {
        return runtime(printerId).getLastState();
    }

    private void lastState(UUID printerId, String value) {
        runtime(printerId).setLastState(value);
    }

    private int lastError(UUID printerId) {
        return runtime(printerId).getLastError();
    }

    private void lastError(UUID printerId, int value) {
        runtime(printerId).setLastError(value);
    }

    private int lastAmsStatus(UUID printerId) {
        return runtime(printerId).getLastAmsStatus();
    }

    private void lastAmsStatus(UUID printerId, int value) {
        runtime(printerId).setLastAmsStatus(value);
    }

    private int lastActiveSlot(UUID printerId) {
        return runtime(printerId).getLastActiveSlot();
    }

    private void lastActiveSlot(UUID printerId, int value) {
        runtime(printerId).setLastActiveSlot(value);
    }

    private int lastProgress(UUID printerId) {
        return runtime(printerId).getLastProgress();
    }

    private void lastProgress(UUID printerId, int value) {
        runtime(printerId).setLastProgress(value);
    }

    private int lastLayer(UUID printerId) {
        return runtime(printerId).getLastLayer();
    }

    private void lastLayer(UUID printerId, int value) {
        runtime(printerId).setLastLayer(value);
    }

    private List<AmsSlot> previousAmsSlots(UUID printerId) {
        return runtime(printerId).getPreviousAmsSlots();
    }

    private void previousAmsSlots(UUID printerId, List<AmsSlot> slots) {
        runtime(printerId).setPreviousAmsSlots(slots);
    }

}
