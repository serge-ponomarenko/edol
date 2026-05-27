package org.spon.edolhub.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final PrinterService printerService;
    private final PrintJobService printJobService;
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

                case "print.started" -> handlePrintStarted(json);

                case "print.finished" -> handlePrintFinished(json);

                case "print.failed" -> handlePrintFailed(json);

                case "print.progress.changed" -> handlePrintProgress(json);

                case "print.metadata.loaded" -> handlePrintMetadata(json);

                case "ams.status.changed" -> handleAmsStatus(json);

                case "ams.slot.changed" -> handleAmsSlot(json);

                default -> log.debug("Unhandled event: {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void handlePrintStarted(JsonNode json) {
        printJobService.start(printerState);
    }

    private void handlePrintFinished(JsonNode json) {
        printJobService.finish(printerState);
    }

    private void handlePrintFailed(JsonNode json) {
        printJobService.cancel(printerState);
    }

    private void handlePrintProgress(JsonNode json) {
        try {
            printJobService.updateProgress(printerState);
        } catch (Exception e) {
            log.error("Session ID {} hasn't been registered.", printerState.getSessionId());
        }
    }

    private void handlePrintMetadata(JsonNode json) {
        printJobService.metadataLoaded(printerState);
    }

    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }
}