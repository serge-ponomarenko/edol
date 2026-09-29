package org.spon.edolhub.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.service.TenantScopeProvider;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edolhub.service.PrinterService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttEventListenerTest {

    @Mock
    private PrinterService printerService;

    @Mock
    private PrinterCatalogSyncService printerCatalogSyncService;

    @Mock
    private PrintJobService printJobService;

    @Mock
    private TenantScopeProvider compatibilityScope;

    @Mock
    private TenantContext.TenantScope tenantScope;

    @InjectMocks
    private MqttEventListener listener;

    @Test
    void skipsMqttPersistenceWhenTheExplicitCompatibilityScopeIsUnavailable() {
        when(compatibilityScope.openIfConfigured("mqtt-event-listener")).thenReturn(Optional.empty());

        listener.handle(MessageBuilder.withPayload("{}").build());

        verify(compatibilityScope).openIfConfigured("mqtt-event-listener");
        verifyNoInteractions(printerService, printerCatalogSyncService, printJobService, tenantScope);
    }

    @Test
    void closesTheMqttCompatibilityScopeAfterProcessingAnIngressEvent() {
        when(compatibilityScope.openIfConfigured("mqtt-event-listener")).thenReturn(Optional.of(tenantScope));
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000801");

        listener.handle(MessageBuilder.withPayload("""
                {"event":"ams.status.changed","printerId":"00000000-0000-0000-0000-000000000801"}
                """).build());

        verify(compatibilityScope).openIfConfigured("mqtt-event-listener");
        verify(tenantScope).close();
        verify(printerCatalogSyncService).synchronize(printerId);
        verify(printerService).getState(printerId);
        verifyNoInteractions(printJobService);
    }
}
