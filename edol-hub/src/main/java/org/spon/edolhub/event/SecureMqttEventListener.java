package org.spon.edolhub.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolhub.service.CoreMqttEventReceiptService;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.DateTimeException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
@ConditionalOnProperty(name = "edol-hub.runtime.mqtt-enabled", havingValue = "true")
public class SecureMqttEventListener {

    private static final int SCHEMA_VERSION = 2;

    private final PrinterService printerService;
    private final PrintJobService printJobService;
    private final TenantContext tenantContext;
    private final CoreMqttEventReceiptService receiptService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "secureMqttInputChannel")
    public void handle(Message<?> message) {
        CoreMqttEventEnvelope envelope;
        try {
            JsonNode json = objectMapper.readTree(message.getPayload().toString());
            envelope = objectMapper.treeToValue(json, CoreMqttEventEnvelope.class);
            validateEnvelope(json, envelope);
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException exception) {
            log.warn("Rejected Core MQTT integration event", exception);
            acknowledge(message);
            return;
        }

        try {
            process(envelope);
        } catch (IllegalArgumentException exception) {
            log.warn("Rejected Core MQTT integration event", exception);
            acknowledge(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not process Core MQTT integration event", exception);
        }
        acknowledge(message);
    }

    private void process(CoreMqttEventEnvelope envelope) {
        try (TenantContext.TenantScope ignored = tenantContext.open(envelope.tenantId())) {
            receiptService.process(envelope, () -> apply(envelope));
        }
    }

    private void acknowledge(Message<?> message) {
        Object callback = message.getHeaders().get(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK);
        if (callback instanceof SimpleAcknowledgment acknowledgment) {
            acknowledgment.acknowledge();
        }
    }

    private void apply(CoreMqttEventEnvelope envelope) {
        PrinterState printerState = printerService.getState(envelope.printerId());
        if (printerState == null) {
            throw new IllegalStateException("Core printer state is unavailable: " + envelope.printerId());
        }

        switch (envelope.eventType()) {
            case "print.started" -> printJobService.start(envelope.printerId(), printerState);
            case "print.finished" -> complete(envelope.printerId(), printerState);
            case "print.failed" -> fail(envelope.printerId(), printerState);
            case "print.progress.changed" -> updateProgress(envelope.printerId(), printerState);
            case "print.metadata.loaded" -> loadMetadata(envelope.printerId(), printerState);
            default -> log.debug("Validated Core MQTT event with no Hub persistence action: {}", envelope.eventType());
        }
    }

    private void complete(UUID printerId, PrinterState printerState) {
        printJobService.start(printerId, printerState);
        printJobService.finish(printerId, printerState);
    }

    private void fail(UUID printerId, PrinterState printerState) {
        printJobService.start(printerId, printerState);
        printJobService.cancel(printerId, printerState);
    }

    private void updateProgress(UUID printerId, PrinterState printerState) {
        printJobService.start(printerId, printerState);
        printJobService.updateProgress(printerId, printerState);
    }

    private void loadMetadata(UUID printerId, PrinterState printerState) {
        printJobService.start(printerId, printerState);
        printJobService.metadataLoaded(printerId, printerState);
    }

    private void validateEnvelope(JsonNode json, CoreMqttEventEnvelope envelope) {
        if (envelope.schemaVersion() != SCHEMA_VERSION
                || envelope.eventId() == null
                || envelope.eventType() == null
                || envelope.tenantId() == null
                || envelope.printerId() == null
                || envelope.timestamp() == null
                || envelope.payload() == null) {
            throw new IllegalArgumentException("Incomplete Core MQTT v2 envelope");
        }
        Instant.parse(envelope.timestamp());
        if (!envelope.eventType().equals(json.required("event").asText())
                || !envelope.printerId().equals(UUID.fromString(json.required("printerId").asText()))) {
            throw new IllegalArgumentException("Core MQTT legacy and envelope identities disagree");
        }
    }
}
