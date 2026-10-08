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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecureMqttEventListenerTest {

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
