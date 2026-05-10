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

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final PrinterService printerService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageService messageService;

    private PrinterState printerState;

    @Value("${telegram.progress-message-step}")
    private int telegramProgressMessageStep;

    private int lastNotifiedProgressMilestone = -1;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event = json.get("event").asText();

            log.info("EdolCore MQTT EVENT: {}", event);

            printerState = printerService.getState();

            switch (event) {

                case "printer.online" -> handlePrinterOnline(json);

                case "printer.offline" -> handlePrinterOffline(json);

                case "print.started" -> handlePrintStarted(json);

                case "print.paused" -> handlePrintPaused(json);

                case "print.running" -> handlePrintRunning(json);

                case "print.finished" -> handlePrintFinished(json);

                case "print.failed" -> handlePrintFailed(json);

                case "print.error" -> handlePrintError(json);

                case "print.progress.changed" -> handlePrintProgress(json);

                case "print.metadata.loaded" -> handlePrintMetadata(json);

                case "print.timelapse" -> handlePrintTimelapse(json);

                case "ams.status.changed" -> handleAmsStatus(json);

                case "ams.slot.changed" -> handleAmsSlot(json);

                default -> log.debug("Unhandled event: {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void handlePrintError(JsonNode json) {
        // TODO
    }

    private void handlePrintTimelapse(JsonNode json) {
        Path videoPath = Path.of(json.get("path").asText());
        messageService.sendTimelapseVideoMessage(videoPath);
    }

    private void handlePrintPaused(JsonNode json) {
        messageService.sendStatusMessage();
    }

    private void handlePrintRunning(JsonNode json) {
        messageService.sendStatusMessage();
    }

    private void handlePrinterOnline(JsonNode json) {
        messageService.sendPrinterOnlineMessage();
    }

    private void handlePrinterOffline(JsonNode json) {
        messageService.sendPrinterOfflineMessage();
    }

    private void handlePrintStarted(JsonNode json) {
        lastNotifiedProgressMilestone = -1;
        messageService.sendPrintStartedMessage();
    }

    private void handlePrintFinished(JsonNode json) {
        messageService.sendStatusMessage();
    }

    private void handlePrintFailed(JsonNode json) {
        messageService.sendStatusMessage();
    }

    private void handlePrintProgress(JsonNode json) {
        int progress = printerState.getProgress();
        if (isProgressMessageMilestone(progress) && progress < 100) {
            messageService.sendStatusMessage();
        }
    }

    private void handlePrintMetadata(JsonNode json) {
        messageService.sendStatusMessage();
    }

    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        //log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }

    private boolean isProgressMessageMilestone(int progress) {
        int milestone = progress / telegramProgressMessageStep;

        if (milestone > lastNotifiedProgressMilestone && milestone > 0) {
            lastNotifiedProgressMilestone = milestone;
            return true;
        }

        return false;
    }
}