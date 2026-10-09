package org.spon.edolams.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.AmsPrinterTenantResolver;
import org.spon.edolams.service.AmsTenantContext;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.time.Instant;


@Component
@RequiredArgsConstructor
@Slf4j
public class MqttEventListener {

    private final AmsSpoolChangerService amsSpoolChangerService;
    private final DeploymentMode deploymentMode;
    private final AmsPrinterTenantResolver tenantResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
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
                tenantScope = tenantResolver.openForEnvelope(envelope.printerId(), envelope.tenantId());
                event = envelope.eventType();
                printerId = envelope.printerId();
                eventPayload = json.required("payload");
            } else {
                event = json.required("event").asText();
                printerId = UUID.fromString(json.required("printerId").asText());
                eventPayload = json;
            }

            try {
                log.info("EdolCore MQTT EVENT: {}", event);

                switch (event) {

                    case "ams.status.changed" -> handleAmsStatus(eventPayload);

                    case "ams.slot.changed" -> handleAmsSlot(eventPayload);

                    case "ams.slot.loaded" -> handleAmsSlotLoaded(printerId, eventPayload);

                    case "ams.slot.unloaded" -> handleAmsSlotUnloaded(eventPayload);

                    default -> log.debug("Unhandled event: {}", event);
                }
            } finally {
                if (tenantScope != null) {
                    tenantScope.close();
                }
            }

        } catch (Exception e) {
            log.error("Failed to process MQTT message", e);
        }
    }

    private void validateEnvelope(JsonNode json, CoreMqttEventEnvelope envelope) {
        if (envelope.schemaVersion() != 2
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

    private void handleAmsSlotUnloaded(JsonNode json) {

    }

    private void handleAmsSlotLoaded(UUID printerId, JsonNode json) {
        int slot = json.get("slot").asInt();
        amsSpoolChangerService.setAmsSpoolIntoSlot(printerId, slot);
    }


    private void handleAmsStatus(JsonNode json) {
        //JsonNode amsNode = json.get("ams");
        //log.info("Ams status changed: {}", amsNode);
    }

    private void handleAmsSlot(JsonNode json) {
        log.info("Ams slot changed: {} -> {}", json.get("prev_slot"), json.get("curr_slot"));
    }
}
