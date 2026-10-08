package org.spon.edolcore.service.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoreIntegrationEventPublisher {

    private static final int SCHEMA_VERSION = 2;
    private static final String LEGACY_EVENT = "event";
    private static final String LEGACY_PRINTER_ID = "printerId";

    private final MessageChannel coreEventsOutboundChannel;
    private final CoreEventTenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    public void publish(String topic, Map<String, Object> legacyPayload) {
        Object printerId = legacyPayload.get(LEGACY_PRINTER_ID);
        Object eventType = legacyPayload.get(LEGACY_EVENT);
        if (!(printerId instanceof UUID typedPrinterId) || !(eventType instanceof String typedEventType)) {
            log.atError()
                    .addKeyValue("topic", topic)
                    .log("Core MQTT integration event has no valid legacy identity");
            return;
        }
        publish(topic, typedPrinterId, typedEventType, legacyPayload);
    }

    public void publish(String topic, UUID printerId, String eventType, Map<String, Object> legacyPayload) {
        try {
            UUID tenantId = tenantResolver.tenantIdForPrinter(printerId)
                    .orElseThrow(() -> new IllegalStateException("Missing persisted tenant ownership for printer " + printerId));
            validateLegacyPayload(printerId, eventType, legacyPayload);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("schemaVersion", SCHEMA_VERSION);
            envelope.put("eventId", UUID.randomUUID());
            envelope.put("eventType", eventType);
            envelope.put("tenantId", tenantId);
            envelope.put(LEGACY_PRINTER_ID, printerId);
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("payload", nestedPayload(legacyPayload));
            envelope.putAll(legacyPayload);

            Message<String> message = MessageBuilder.withPayload(objectMapper.writeValueAsString(envelope))
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build();
            coreEventsOutboundChannel.send(message);
        } catch (Exception exception) {
            log.atError()
                    .addKeyValue("topic", topic)
                    .addKeyValue(LEGACY_PRINTER_ID, printerId)
                    .addKeyValue("eventType", eventType)
                    .log("Core MQTT integration event was not published", exception);
        }
    }

    private void validateLegacyPayload(UUID printerId, String eventType, Map<String, Object> legacyPayload) {
        if (!printerId.equals(legacyPayload.get(LEGACY_PRINTER_ID))
                || !eventType.equals(legacyPayload.get(LEGACY_EVENT))) {
            throw new IllegalArgumentException("Core MQTT legacy event identity does not match envelope identity");
        }
    }

    private Map<String, Object> nestedPayload(Map<String, Object> legacyPayload) {
        Map<String, Object> payload = new LinkedHashMap<>(legacyPayload);
        payload.remove(LEGACY_EVENT);
        payload.remove(LEGACY_PRINTER_ID);
        return payload;
    }
}
