package org.spon.edolams.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.PrinterService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final PrinterService printerService;
    private final AmsSpoolChangerService amsSpoolChangerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrinterState printerState;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event = json.get("event").asText();

            log.info("EdolCore MQTT EVENT: {}", event);

            printerState = printerService.getState();

            switch (event) {

                case "ams.status.changed" -> handleAmsStatus(json);

                case "ams.slot.changed" -> handleAmsSlot(json);

                case "ams.slot.loaded" -> handleAmsSlotLoaded(json);

                case "ams.slot.unloaded" -> handleAmsSlotUnloaded(json);

                default -> log.debug("Unhandled event: {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void handleAmsSlotUnloaded(JsonNode json) {

    }

    private void handleAmsSlotLoaded(JsonNode json) {
        int slot = json.get("slot").asInt();
        amsSpoolChangerService.setAmsSpoolIntoSlot(slot);
    }


    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }
}