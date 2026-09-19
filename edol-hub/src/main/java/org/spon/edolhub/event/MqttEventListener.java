package org.spon.edolhub.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edolhub.service.PrinterService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final PrinterService printerService;
    private final PrinterCatalogSyncService printerCatalogSyncService;
    private final PrintJobService printJobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event = json.get("event").asText();
            UUID printerId = UUID.fromString(json.get("printerId").asText());

            log.info("EdolCore MQTT EVENT: {}", event);

            printerCatalogSyncService.synchronize(printerId);
            PrinterState printerState = printerService.getState(printerId);

            if (printerState == null) {
                log.warn("Skipping event {} because printer state is unavailable for {}", event, printerId);
                return;
            }

            log.info(
                    "State for event {}: sessionId={}, printing={}, filaments={}",
                    event,
                    printerState != null ? printerState.getSessionId() : null,
                    printerState != null && printerState.isPrinting(),
                    printerState != null && printerState.getFilaments() != null
                            ? printerState.getFilaments().size()
                            : null
            );

            switch (event) {
                case "print.started" -> handlePrintStarted(printerId, printerState);
                case "print.finished" -> handlePrintFinished(printerId, printerState);
                case "print.failed" -> handlePrintFailed(printerId, printerState);
                case "print.progress.changed" -> handlePrintProgress(printerId, printerState);
                case "print.metadata.loaded" -> handlePrintMetadata(printerId, printerState);
                case "ams.status.changed" -> handleAmsStatus(json);
                case "ams.slot.changed" -> handleAmsSlot(json);
                default -> log.debug("Unhandled event: {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void handlePrintStarted(UUID printerId, PrinterState printerState) {
        printJobService.start(printerId, printerState);
    }

    private void handlePrintFinished(UUID printerId, PrinterState printerState) {
        printJobService.finish(printerId, printerState);
    }

    private void handlePrintFailed(UUID printerId, PrinterState printerState) {
        printJobService.cancel(printerId, printerState);
    }

    private void handlePrintProgress(UUID printerId, PrinterState printerState) {
        try {
            printJobService.updateProgress(printerId, printerState);
        } catch (Exception e) {
            log.error("Session ID {} hasn't been registered.", printerState.getSessionId());
        }
    }

    private void handlePrintMetadata(UUID printerId, PrinterState printerState) {
        printJobService.metadataLoaded(printerId, printerState);
    }

    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }
}
