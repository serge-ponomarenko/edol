package org.spon.edolnotify.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.service.MessageService;
import org.spon.edolnotify.service.PrinterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final PrinterService printerService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageService messageService;

    @Value("${telegram.progress-message-step}")
    private int telegramProgressMessageStep;

    private final Map<UUID, Integer> lastNotifiedProgressMilestone = new ConcurrentHashMap<>();

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event = json.get("event").asText();
            UUID printerId = UUID.fromString(json.required("printerId").asText());

            log.info("EdolCore MQTT EVENT: {}", event);

            PrinterState printerState = printerService.getState(printerId);

            switch (event) {

                case "printer.online" -> handlePrinterOnline(printerId);

                case "printer.offline" -> handlePrinterOffline(printerId);

                case "print.started" -> handlePrintStarted(printerId);

                case "print.paused" -> handlePrintPaused(printerId);

                case "print.running" -> handlePrintRunning(printerId);

                case "print.finished" -> handlePrintFinished(printerId);

                case "print.failed" -> handlePrintFailed(printerId);

                case "print.error" -> handlePrintError(printerId);

                case "print.progress.changed" -> handlePrintProgress(printerId, printerState);

                case "print.metadata.loaded" -> handlePrintMetadata(printerId);

                case "print.timelapse" -> handlePrintTimelapse(printerId, json);

                case "ams.status.changed" -> handleAmsStatus(json);

                case "ams.slot.changed" -> handleAmsSlot(json);

                default -> log.debug("Unhandled event: {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void handlePrintError(UUID printerId) {
        // TODO
    }

    private void handlePrintTimelapse(UUID printerId, JsonNode json) {
        Path videoPath = Path.of(json.get("path").asText());
        messageService.sendTimelapseVideoMessage(printerId, videoPath);
    }

    private void handlePrintPaused(UUID printerId) {
        messageService.sendStatusMessage(printerId);
    }

    private void handlePrintRunning(UUID printerId) {
        messageService.sendStatusMessage(printerId);
    }

    private void handlePrinterOnline(UUID printerId) {
        messageService.sendPrinterOnlineMessage(printerId);
    }

    private void handlePrinterOffline(UUID printerId) {
        messageService.sendPrinterOfflineMessage(printerId);
    }

    private void handlePrintStarted(UUID printerId) {
        lastNotifiedProgressMilestone.put(printerId, -1);
        messageService.sendPrintStartedMessage(printerId);
    }

    private void handlePrintFinished(UUID printerId) {
        messageService.sendStatusMessage(printerId);
    }

    private void handlePrintFailed(UUID printerId) {
        messageService.sendStatusMessage(printerId);
    }

    private void handlePrintProgress(UUID printerId, PrinterState printerState) {
        int progress = printerState.getProgress();
        if (isProgressMessageMilestone(printerId, progress) && progress < 100) {
            messageService.sendStatusMessage(printerId);
        }
    }

    private void handlePrintMetadata(UUID printerId) {
        messageService.sendStatusMessage(printerId);
    }

    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        //log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }

    private boolean isProgressMessageMilestone(UUID printerId, int progress) {
        int milestone = progress / telegramProgressMessageStep;

        if (milestone > lastNotifiedProgressMilestone.getOrDefault(printerId, -1) && milestone > 0) {
            lastNotifiedProgressMilestone.put(printerId, milestone);
            return true;
        }

        return false;
    }
}
