package org.spon.edolhub.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolhub.service.CoreMqttEventReceiptService;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.UUID;
import org.spon.edol.mqtt.CoreMqttIngressObservation;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecureMqttEventListenerTest {

    @Test
    void acceptsV3WithoutLegacyFieldsThroughTheExistingReceiptBoundary() throws Exception {
        PrinterService printers = mock(PrinterService.class);
        PrintJobService jobs = mock(PrintJobService.class);
        TenantContext tenants = mock(TenantContext.class);
        CoreMqttEventReceiptService receipts = mock(CoreMqttEventReceiptService.class);
        var listener = listener(printers, jobs, tenants, receipts);
        UUID tenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        ObjectNode json = (ObjectNode) new ObjectMapper().readTree(envelope(tenantId, printerId, "print.started"));
        json.put("schemaVersion", 3);
        json.remove("event");
        listener.handle(MessageBuilder.withPayload(json.toString()).build());

        verify(tenants).open(tenantId);
        verify(receipts).process(any(CoreMqttEventEnvelope.class), any(Runnable.class));
        assertThat(observation(listener).snapshot().validatedV3()).isEqualTo(1);
        assertThat(observation(listener).snapshot().withLegacyEvent()).isZero();
    }

    @Test
    void rejectsInvalidVersionsAndLegacyOnlyMessagesBeforeOpeningTenantContext() throws Exception {
        PrinterService printers = mock(PrinterService.class);
        PrintJobService jobs = mock(PrintJobService.class);
        TenantContext tenants = mock(TenantContext.class);
        CoreMqttEventReceiptService receipts = mock(CoreMqttEventReceiptService.class);
        var listener = listener(printers, jobs, tenants, receipts);
        String v2 = envelope(UUID.randomUUID(), UUID.randomUUID(), "print.started");
        for (String invalid : new String[]{v2.replace("\"schemaVersion\":2", "\"schemaVersion\":4"),
                v2.replace("\"schemaVersion\":2", "\"schemaVersion\":2.5"),
                "{\"event\":\"print.started\"}", "null", "", "{malformed}"}) {
            var acknowledgment = mock(SimpleAcknowledgment.class);
            listener.handle(MessageBuilder.withPayload(invalid)
                    .setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, acknowledgment).build());
            verify(acknowledgment).acknowledge();
        }
        verifyNoInteractions(tenants, receipts, printers, jobs);
        assertThat(observation(listener).snapshot().rejectedEnvelope()).isEqualTo(6);
    }

    @Test
    void countsTenantRejectionAndAcknowledgesItOnlyOnce() throws Exception {
        PrinterService printers = mock(PrinterService.class);
        PrintJobService jobs = mock(PrintJobService.class);
        TenantContext tenants = mock(TenantContext.class);
        CoreMqttEventReceiptService receipts = mock(CoreMqttEventReceiptService.class);
        doThrow(new IllegalArgumentException("Wrong tenant")).when(receipts)
                .process(any(CoreMqttEventEnvelope.class), any(Runnable.class));
        var listener = listener(printers, jobs, tenants, receipts);
        var acknowledgment = mock(SimpleAcknowledgment.class);
        listener.handle(MessageBuilder.withPayload(envelope(UUID.randomUUID(), UUID.randomUUID(), "print.started"))
                .setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, acknowledgment).build());
        verifyNoInteractions(printers, jobs);
        verify(acknowledgment).acknowledge();
        assertThat(observation(listener).snapshot().validatedV2()).isEqualTo(1);
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(1);
        assertThat(observation(listener).snapshot().rejectedEnvelope()).isZero();
    }

    private CoreMqttIngressObservation observation(SecureMqttEventListener listener) {
        return (CoreMqttIngressObservation) ReflectionTestUtils.getField(listener, "ingress");
    }

    @Test
    void opensTheEnvelopeTenantAndAppliesAValidatedEventOnce() throws Exception {
        PrinterService printerService = mock(PrinterService.class);
        PrintJobService printJobService = mock(PrintJobService.class);
        TenantContext tenantContext = mock(TenantContext.class);
        TenantContext.TenantScope scope = mock(TenantContext.TenantScope.class);
        CoreMqttEventReceiptService receiptService = mock(CoreMqttEventReceiptService.class);
        SimpleAcknowledgment acknowledgment = mock(SimpleAcknowledgment.class);
        UUID tenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        when(tenantContext.open(tenantId)).thenReturn(scope);
        when(printerService.getState(printerId)).thenReturn(new PrinterState());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        }).when(receiptService).process(any(CoreMqttEventEnvelope.class), any(Runnable.class));

        SecureMqttEventListener listener = listener(printerService, printJobService, tenantContext, receiptService);
        listener.handle(MessageBuilder.withPayload(envelope(tenantId, printerId, "print.progress.changed"))
                .setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, acknowledgment)
                .build());

        verify(tenantContext).open(tenantId);
        verify(receiptService).process(any(CoreMqttEventEnvelope.class), any(Runnable.class));
        verify(printJobService).updateProgress(eq(printerId), any(PrinterState.class));
        verify(scope).close();
        verify(acknowledgment).acknowledge();
    }

    @Test
    void rejectsLegacyAndEnvelopeEventTypeMismatchBeforeOpeningTenantContext() throws Exception {
        PrinterService printerService = mock(PrinterService.class);
        PrintJobService printJobService = mock(PrintJobService.class);
        TenantContext tenantContext = mock(TenantContext.class);
        CoreMqttEventReceiptService receiptService = mock(CoreMqttEventReceiptService.class);
        SimpleAcknowledgment acknowledgment = mock(SimpleAcknowledgment.class);
        UUID tenantId = UUID.randomUUID();

        SecureMqttEventListener listener = listener(printerService, printJobService, tenantContext, receiptService);
        listener.handle(MessageBuilder.withPayload(envelope(tenantId, UUID.randomUUID(), "print.started")
                .replace("\"event\":\"print.started\"", "\"event\":\"print.finished\""))
                .setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, acknowledgment)
                .build());

        verifyNoInteractions(tenantContext, receiptService, printerService, printJobService);
        verify(acknowledgment).acknowledge();
    }

    private SecureMqttEventListener listener(
            PrinterService printerService,
            PrintJobService printJobService,
            TenantContext tenantContext,
            CoreMqttEventReceiptService receiptService
    ) {
        return new SecureMqttEventListener(
                printerService,
                printJobService,
                tenantContext,
                receiptService,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private String envelope(UUID tenantId, UUID printerId, String eventType) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of(
                "schemaVersion", 2,
                "eventId", UUID.randomUUID(),
                "eventType", eventType,
                "tenantId", tenantId,
                "printerId", printerId,
                "timestamp", "2026-10-08T12:00:00Z",
                "payload", Map.of(),
                "event", eventType
        ));
    }
}
