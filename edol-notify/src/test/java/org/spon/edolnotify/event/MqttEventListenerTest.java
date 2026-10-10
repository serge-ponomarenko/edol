package org.spon.edolnotify.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.config.NotifyRecipientProperties;
import org.spon.edolnotify.service.MessageService;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.spon.edolnotify.service.PrinterService;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttEventListenerTest {

    private static final UUID FIRST_PRINTER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_PRINTER = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Mock
    private PrinterService printerService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MqttEventListener listener;

    @Test
    void routesNotificationsToThePrinterDeclaredByEachMqttEvent() {
        when(printerService.getState(FIRST_PRINTER)).thenReturn(new PrinterState());
        when(printerService.getState(SECOND_PRINTER)).thenReturn(new PrinterState());
        ReflectionTestUtils.setField(listener, "telegramProgressMessageStep", 10);

        listener.handle(new GenericMessage<>("{\"event\":\"printer.online\",\"printerId\":\"" + FIRST_PRINTER + "\"}"));
        listener.handle(new GenericMessage<>("{\"event\":\"printer.offline\",\"printerId\":\"" + SECOND_PRINTER + "\"}"));

        verify(messageService).sendPrinterOnlineMessage(FIRST_PRINTER);
        verify(messageService).sendPrinterOfflineMessage(SECOND_PRINTER);
    }

    @Test
    void establishesEachV2EventTenantBeforeCallingCore() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        NotifyTenantContext tenantContext = new NotifyTenantContext();
        NotifyRecipientResolver resolver = new NotifyRecipientResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new NotifyRecipientProperties(List.of(
                        new NotifyRecipientProperties.TenantRecipient(tenantA, List.of(101L)),
                        new NotifyRecipientProperties.TenantRecipient(tenantB, List.of(201L))
                )),
                tenantContext,
                0
        );
        ReflectionTestUtils.invokeMethod(resolver, "validateMappings");
        MqttEventListener secureListener = new MqttEventListener(
                printerService,
                messageService,
                DeploymentMode.SECURE_MULTI_TENANT,
                resolver
        );
        ReflectionTestUtils.setField(secureListener, "telegramProgressMessageStep", 10);
        List<UUID> coreCallTenants = new ArrayList<>();
        List<UUID> messageTenants = new ArrayList<>();
        when(printerService.getState(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            coreCallTenants.add(tenantContext.currentTenantId());
            return new PrinterState();
        });
        doAnswer(invocation -> {
            messageTenants.add(tenantContext.currentTenantId());
            return null;
        }).when(messageService).sendPrinterOnlineMessage(org.mockito.ArgumentMatchers.any());

        secureListener.handle(new GenericMessage<>(v2OnlineEvent(tenantA, FIRST_PRINTER)));
        secureListener.handle(new GenericMessage<>(v2OnlineEvent(tenantB, SECOND_PRINTER)));

        assertThat(coreCallTenants).containsExactly(tenantA, tenantB);
        assertThat(messageTenants).containsExactly(tenantA, tenantB);
        verify(messageService).sendPrinterOnlineMessage(FIRST_PRINTER);
        verify(messageService).sendPrinterOnlineMessage(SECOND_PRINTER);
    }

    private String v2OnlineEvent(UUID tenantId, UUID printerId) {
        return "{\"schemaVersion\":2,\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"printer.online\",\"tenantId\":\"" + tenantId
                + "\",\"printerId\":\"" + printerId
                + "\",\"timestamp\":\"2026-10-10T07:24:38.460Z\",\"payload\":{},"
                + "\"event\":\"printer.online\"}";
    }
}
