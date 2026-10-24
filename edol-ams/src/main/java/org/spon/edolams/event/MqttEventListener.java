package org.spon.edolams.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edol.mqtt.CoreMqttIngressObservation;
import jakarta.annotation.PostConstruct;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.AmsPrinterTenantResolver;
import org.spon.edolams.service.AmsTenantContext;
import org.spon.edolams.service.UnmappedAmsPrinterException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private static final String LEGACY_EVENT = "event";

    private final AmsSpoolChangerService amsSpoolChangerService;
    private final DeploymentMode deploymentMode;
    private final AmsPrinterTenantResolver tenantResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoreMqttIngressObservation ingress = new CoreMqttIngressObservation();

    @PostConstruct
    void initializeObservations() {
        if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
            log.info("Core MQTT ingress initialized: consumer=ams, counters={}", ingress.snapshot());
        }
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        boolean validatedEnvelope = false;
        CoreMqttEventEnvelope validated = null;
        String stage = "envelope";
        try {
            String payload = message.getPayload().toString();

            JsonNode json = objectMapper.readTree(payload);
            String event;
            UUID printerId;
            JsonNode eventPayload;
            AmsTenantContext.Scope tenantScope = null;
            if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
                CoreMqttEventEnvelope envelope = objectMapper.treeToValue(json, CoreMqttEventEnvelope.class);
                validateEnvelope(json, envelope);
                ingress.validated(envelope.schemaVersion(), json.has(LEGACY_EVENT));
                validatedEnvelope = true;
                validated = envelope;
                log.info("Core MQTT ingress validated: consumer=ams, counters={}", ingress.snapshot());
                stage = "tenant_mapping";
                tenantScope = tenantResolver.openForEnvelope(envelope.printerId(), envelope.tenantId());
                event = envelope.eventType();
                printerId = envelope.printerId();
                eventPayload = json.required("payload");
            } else {
                event = json.required(LEGACY_EVENT).asText();
                printerId = UUID.fromString(json.required("printerId").asText());
                eventPayload = json;
            }

            try {
                stage = "handler";
                if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
                    log.info("EdolCore MQTT EVENT: {}", event);
                }

                switch (event) {

                    case "ams.status.changed", "ams.slot.unloaded" ->
                            log.debug("AMS status/unload event does not change staged spools");

                    case "ams.slot.changed" -> handleAmsSlot(eventPayload);

                    case "ams.slot.loaded" -> handleAmsSlotLoaded(printerId, eventPayload);

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
                    logProcessingFailure(validated, stage, e);
                } else {
                    ingress.rejectEnvelope();
                    log.warn("Rejected Core MQTT envelope: consumer=ams, category={}, counters={}",
                            e.getClass().getSimpleName(), ingress.snapshot());
                }
            } else {
                log.error("Failed to process MQTT message", e);
            }
        }
    }

    private void logProcessingFailure(CoreMqttEventEnvelope envelope, String stage, Exception exception) {
        String reason = exception instanceof UnmappedAmsPrinterException ? "unmapped_printer" : stage;
        Throwable cause = exception;
        for (int depth = 0; depth < 8 && cause.getCause() != null && cause.getCause() != cause; depth++) {
            cause = cause.getCause();
        }
        Integer status = exception instanceof RestClientResponseException response
                ? response.getStatusCode().value() : null;
        // Validated UUIDs, allowlisted type and class names only; messages may contain secrets or SQL.
        log.warn("Core MQTT processing failed: consumer=ams, event={}, type={}, tenant={}, printer={}, stage={}, reason={}, category={}, cause={}, http_status={}, counters={}",
                envelope.eventId(), diagnosticEventType(envelope.eventType()), envelope.tenantId(), envelope.printerId(),
                stage, reason, exception.getClass().getSimpleName(), cause.getClass().getSimpleName(), status,
                ingress.snapshot());
    }

    private String diagnosticEventType(String type) {
        return switch (type) {
            case "ams.status.changed", "ams.slot.unloaded", "ams.slot.changed", "ams.slot.loaded" -> type;
            default -> "other";
        };
    }

    private void validateEnvelope(JsonNode json, CoreMqttEventEnvelope envelope) {
        if (json == null || !json.isObject() || !json.required("schemaVersion").isIntegralNumber()
                || (json.has(LEGACY_EVENT) && !json.required(LEGACY_EVENT).isTextual())) {
            throw new IllegalArgumentException("Invalid Core MQTT envelope field types");
        }
        envelope.validate(json.has(LEGACY_EVENT) ? json.get(LEGACY_EVENT).asText() : null);
    }

    private void handleAmsSlotLoaded(UUID printerId, JsonNode json) {
        int slot = json.get("slot").asInt();
        amsSpoolChangerService.setAmsSpoolIntoSlot(printerId, slot);
    }


    private void handleAmsSlot(JsonNode json) {
        log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }
}
