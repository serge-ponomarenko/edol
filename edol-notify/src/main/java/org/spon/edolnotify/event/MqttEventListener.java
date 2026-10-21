package org.spon.edolnotify.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.model.PrinterState;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edol.mqtt.CoreMqttIngressObservation;
import jakarta.annotation.PostConstruct;
import org.spon.edolnotify.service.MessageService;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
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

    private static final String LEGACY_EVENT = "event";

    private final PrinterService printerService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageService messageService;
    private final DeploymentMode deploymentMode;
    private final NotifyRecipientResolver recipientResolver;
    private final CoreMqttIngressObservation ingress = new CoreMqttIngressObservation();

    @PostConstruct
    void initializeObservations() {
        if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
            log.info("Core MQTT ingress initialized: consumer=notify, counters={}", ingress.snapshot());
        }
    }

    @Value("${telegram.progress-message-step}")
    private int telegramProgressMessageStep;

    private final Map<UUID, Integer> lastNotifiedProgressMilestone = new ConcurrentHashMap<>();

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        boolean validatedEnvelope = false;
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event;
            UUID printerId;
            JsonNode eventPayload;
            NotifyTenantContext.Scope tenantScope = null;
            if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
                CoreMqttEventEnvelope envelope = objectMapper.treeToValue(json, CoreMqttEventEnvelope.class);
                validateEnvelope(json, envelope);
                ingress.validated(envelope.schemaVersion(), json.has(LEGACY_EVENT));
                validatedEnvelope = true;
                log.info("Core MQTT ingress validated: consumer=notify, counters={}", ingress.snapshot());
                tenantScope = recipientResolver.openForEvent(envelope.tenantId());
                event = envelope.eventType();
                printerId = envelope.printerId();
                eventPayload = json.required("payload");
            } else {
                event = json.required(LEGACY_EVENT).asText();
                printerId = UUID.fromString(json.required("printerId").asText());
                eventPayload = json;
            }

            try {
                if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
                    log.info("EdolCore MQTT EVENT: {}", event);
                }
                PrinterState printerState = printerService.getState(printerId);

                switch (event) {

                case "printer.online" -> handlePrinterOnline(printerId);

                case "printer.offline" -> handlePrinterOffline(printerId);

                case "print.started" -> handlePrintStarted(printerId);

                case "print.paused" -> handlePrintPaused(printerId);

                case "print.running" -> handlePrintRunning(printerId);

                case "print.finished" -> handlePrintFinished(printerId);

                case "print.failed" -> handlePrintFailed(printerId);

                case "print.error" -> log.debug("Print error event received for printer {}", printerId);

                case "print.progress.changed" -> handlePrintProgress(printerId, printerState);

                case "print.metadata.loaded" -> handlePrintMetadata(printerId);

                    case "print.timelapse" -> handlePrintTimelapse(printerId, eventPayload);

                    default -> log.debug("Unhandled event: {}", event);
                }
            } finally {
                if (tenantScope != null) {
                    tenantScope.close();
                }
            }

        } catch (Exception e) {
            if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
                if (validatedEnvelope) {
                    ingress.processingFailed();
                } else {
                    ingress.rejectEnvelope();
                }
                log.warn("Rejected Core MQTT ingress: consumer=notify, counters={}", ingress.snapshot());
            } else {
                log.error("Failed to process MQTT message", e);
            }
        }
    }

    private void validateEnvelope(JsonNode json, CoreMqttEventEnvelope envelope) {
        if (json == null || !json.isObject() || !json.required("schemaVersion").isIntegralNumber()
                || (json.has(LEGACY_EVENT) && !json.required(LEGACY_EVENT).isTextual())) {
            throw new IllegalArgumentException("Invalid Core MQTT envelope field types");
        }
        envelope.validate(json.has(LEGACY_EVENT) ? json.get(LEGACY_EVENT).asText() : null);
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

    private boolean isProgressMessageMilestone(UUID printerId, int progress) {
        int milestone = progress / telegramProgressMessageStep;

        if (milestone > lastNotifiedProgressMilestone.getOrDefault(printerId, -1) && milestone > 0) {
            lastNotifiedProgressMilestone.put(printerId, milestone);
            return true;
        }

        return false;
    }
}
