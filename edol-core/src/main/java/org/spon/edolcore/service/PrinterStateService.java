package org.spon.edolcore.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.AmsState;
import org.spon.edol.model.ExtTray;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.AmsEvent;
import org.spon.edolcore.event.AmsEventType;
import org.spon.edolcore.event.PrinterEvent;
import org.spon.edolcore.event.PrinterEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
@Slf4j
@Service
public class PrinterStateService {

    private final PrinterState state = new PrinterState();
    private final ApplicationEventPublisher events;

    private String lastState = "IDLE";
    private int lastError = 0;
    private int lastAmsStatus = -1;
    private int lastActiveSlot = -1;
    private int lastProgress = -1;
    private int lastLayer = -1;
    private List<AmsSlot> previousAmsSlots = new ArrayList<>();

    public synchronized void update(JsonNode print) {

        List<PrinterEvent> pendingEvents = new ArrayList<>();
        List<AmsEvent> pendingAmsEvents = new ArrayList<>();

        // ---------- gcode_state ----------
        if (print.has("gcode_state")) {
            String newState = print.get("gcode_state").asText();

            if (!newState.equals(lastState)) {
                log.info("### G-code new state: {}. Old state: {}", newState, lastState);

                detectStateEvents(lastState, newState, pendingEvents);

                state.setGcodeState(newState);
                lastState = newState;
            }
        }


        // ---------- progress ----------
        if (print.has("mc_percent")) {
            int progress = print.get("mc_percent").asInt();

            if (progress != lastProgress) {
                state.setProgress(progress);

                pendingEvents.add(
                        new PrinterEvent(
                                PrinterEventType.PROGRESS_CHANGED,
                                state.getCurrentFile(),
                                null,
                                progress,
                                null
                        )
                );
                lastProgress = progress;
            }

        }

        // ---------- layer ----------
        if (print.has("layer_num")) {
            int layer = print.get("layer_num").asInt();

            if (layer != lastLayer) {
                state.setLayer(layer);

                pendingEvents.add(
                        new PrinterEvent(
                                PrinterEventType.LAYER_CHANGED,
                                state.getCurrentFile(),
                                layer,
                                null,
                                null
                        )
                );

                lastLayer = layer;
            }
        }

        // ---------- error ----------
        if (print.has("print_error")) {
            int error = print.get("print_error").asInt();

            if (error != 0 && error != lastError) {

                pendingEvents.add(
                        new PrinterEvent(
                                PrinterEventType.PRINT_ERROR,
                                state.getCurrentFile(),
                                null,
                                null,
                                error
                        )
                );

                lastError = error;
            }
        }

        // ---------- AMS ----------
        if (print.has("ams_status")) {
            JsonNode amsStatus = print.get("ams_status");
            int ams = amsStatus.asInt();

            if (ams != lastAmsStatus) {
                pendingEvents.add(
                        new PrinterEvent(
                                PrinterEventType.AMS_STATUS_CHANGED,
                                state.getCurrentFile(),
                                null,
                                null,
                                null
                        )
                );

                lastAmsStatus = ams;

            }
        }

        if (print.has("ams")) {
            updateAms(print.get("ams"), pendingAmsEvents);
        }

        if (print.has("ams_mapping")) {
            JsonNode amsMapping = print.get("ams_mapping");
            List<Integer> amsMappingList = new ArrayList<>();
            for (JsonNode node : amsMapping) {
                amsMappingList.add(node.asInt());
            }
            state.setAmsMapping(amsMappingList);
        }

        if (print.has("vt_tray")) {
            updateExtTray(print.get("vt_tray"));
        }

        if (print.has("ams") && print.get("ams").has("tray_now")) {

            int active = print.get("ams").get("tray_now").asInt();

            state.getAms().setActiveSlot(active);

            state.setExternalSpoolUsed(active == 254);

            if (active != lastActiveSlot) {

                pendingEvents.add(
                        new PrinterEvent(
                                PrinterEventType.AMS_SLOT_CHANGED,
                                state.getCurrentFile(),
                                null,
                                null,
                                null
                        )
                );

                state.getAms().setPreviousSlot(lastActiveSlot);
                lastActiveSlot = active;
            }
        }

        if (print.has("total_layer_num"))
            state.setTotalLayers(print.get("total_layer_num").asInt());

        if (print.has("mc_remaining_time"))
            state.setRemainingTime(print.get("mc_remaining_time").asInt());

        if (print.has("nozzle_temper"))
            state.setNozzleTemp(print.get("nozzle_temper").asDouble());

        if (print.has("nozzle_target_temper"))
            state.setNozzleTargetTemp(print.get("nozzle_target_temper").asDouble());

        if (print.has("bed_temper"))
            state.setBedTemp(print.get("bed_temper").asDouble());

        if (print.has("bed_target_temper"))
            state.setBedTargetTemp(print.get("bed_target_temper").asDouble());

        if (print.has("gcode_file"))
            state.setCurrentFile(print.get("gcode_file").asText());

        if (print.has("subtask_name"))
            state.setCurrentTask(print.get("subtask_name").asText());

        if (print.has("wifi_signal"))
            state.setWifiSignal(print.get("wifi_signal").asText());

        if (print.has("spd_mag"))
            state.setSpeed(print.get("spd_mag").asInt());

        pendingEvents.forEach(events::publishEvent);
        pendingAmsEvents.forEach(events::publishEvent);

    }

    private void updateAms(JsonNode amsNode, List<AmsEvent> pendingAmsEvents) {

        if (!amsNode.has("ams"))
            return;

        JsonNode amsArray = amsNode.get("ams");

        if (amsArray.isEmpty())
            return;

        JsonNode ams = amsArray.get(0);

        AmsState amsState = new AmsState();

        amsState.setAmsId(ams.get("id").asInt());

        if (ams.has("temp"))
            amsState.setTemperature(ams.get("temp").asDouble());

        if (ams.has("humidity"))
            amsState.setHumidity(ams.get("humidity").asInt());

        if (ams.has("humidity_raw"))
            amsState.setHumidityRaw(ams.get("humidity_raw").asInt());

        List<AmsSlot> slots = new ArrayList<>();

        JsonNode trays = ams.get("tray");

        for (JsonNode tray : trays) {

            AmsSlot slot = new AmsSlot();

            slot.setId(tray.get("id").asInt());

            if (tray.has("tray_type"))
                slot.setFilamentType(tray.get("tray_type").asText());

            if (tray.has("tray_sub_brands"))
                slot.setFilamentBrand(tray.get("tray_sub_brands").asText());

            if (tray.has("tray_info_idx"))
                slot.setFilamentBrandIndex(tray.get("tray_info_idx").asText());

            if (tray.has("tray_color"))
                slot.setColor(tray.get("tray_color").asText());

            if (tray.has("remain"))
                slot.setRemaining(tray.get("remain").asInt());

            slots.add(slot);
        }

        if (!previousAmsSlots.equals(slots)) {
            previousAmsSlots.forEach(previousSlot -> {
                if (previousSlot.isEmpty() && !slots.get(previousSlot.getId()).isEmpty()) {
                    log.warn("[AMS] New Spool has been loaded into slot {}", previousSlot.getId());
                    pendingAmsEvents.add(
                            new AmsEvent(
                                    AmsEventType.AMS_SLOT_LOADED,
                                    previousSlot.getId()
                            )
                    );
                }
                if (!previousSlot.isEmpty() && slots.get(previousSlot.getId()).isEmpty()) {
                    log.warn("[AMS] Spool has been unloaded from slot {}", previousSlot.getId());
                    pendingAmsEvents.add(
                            new AmsEvent(
                                    AmsEventType.AMS_SLOT_UNLOADED,
                                    previousSlot.getId()
                            )
                    );
                }
            });
        }

        amsState.setSlots(slots);
        previousAmsSlots = slots;

        if (amsNode.has("tray_tar"))
            amsState.setActiveSlot(amsNode.get("tray_tar").asInt());

        if (amsNode.has("tray_now"))
            amsState.setActiveSlot(amsNode.get("tray_now").asInt());

        state.setAms(amsState);
    }

    private void updateExtTray(JsonNode vtTrayNode) {
        ExtTray extTray = new ExtTray();

        if (vtTrayNode.has("tray_type"))
            extTray.setFilamentType(vtTrayNode.get("tray_type").asText());

        if (vtTrayNode.has("tray_sub_brands"))
            extTray.setFilamentBrand(vtTrayNode.get("tray_sub_brands").asText());

        if (vtTrayNode.has("tray_info_idx"))
            extTray.setFilamentBrandIndex(vtTrayNode.get("tray_info_idx").asText());

        if (vtTrayNode.has("tray_color"))
            extTray.setColor(vtTrayNode.get("tray_color").asText());

        if (vtTrayNode.has("remain"))
            extTray.setRemaining(vtTrayNode.get("remain").asInt());

        state.setExtTray(extTray);
    }

    private void detectStateEvents(String oldState, String newState, List<PrinterEvent> pendingEvents) {
        if (oldState.equals(newState))
            return;

        if (
                ("IDLE".equals(oldState) || "FINISH".equals(oldState) || "FAILED".equals(oldState))
                        && ("PREPARE".equals(newState) || "RUNNING".equals(newState))
        ) {
            pendingEvents.add(createPrintEvent(PrinterEventType.PRINT_STARTED));
        }

        if (
                ("PREPARE".equals(oldState) || "PAUSE".equals(oldState))
                        && "RUNNING".equals(newState)
        ) {
            pendingEvents.add(createPrintEvent(PrinterEventType.PRINT_RUNNING));
        }

        if (!"PAUSE".equals(oldState) && "PAUSE".equals(newState)) {
            pendingEvents.add(createPrintEvent(PrinterEventType.PRINT_PAUSED));
        }

        if (!"FINISH".equals(oldState) && "FINISH".equals(newState)
        ) {
            pendingEvents.add(createPrintEvent(PrinterEventType.PRINT_FINISHED));
        }

        if (!"FAILED".equals(oldState) && "FAILED".equals(newState)) {
            pendingEvents.add(createPrintEvent(PrinterEventType.PRINT_FAILED));
        }

    }

    private PrinterEvent createPrintEvent(PrinterEventType type) {
        return new PrinterEvent(
                type,
                state.getCurrentFile(),
                null,
                null,
                null
        );
    }

    public void publish(PrinterEventType type) {
        events.publishEvent(createPrintEvent(type));
    }

}