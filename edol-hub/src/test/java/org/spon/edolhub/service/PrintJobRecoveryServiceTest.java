package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.spool.AllocationPreviewRuntimeSyncService;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class PrintJobRecoveryServiceTest {

    @Mock
    private PrinterService printerService;

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrintRuntimeStateService runtimeStateService;

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private PrinterCatalogSyncService printerCatalogSyncService;

    @Mock
    private TenantScopeProvider compatibilityScope;

    @Mock
    private TenantContext.TenantScope tenantScope;

    @InjectMocks
    private PrintJobRecoveryService recoveryService;

    @Test
    void synchronizesCatalogOnceBeforeRecoveringPrinters() {
        when(compatibilityScope.openIfConfigured(anyString())).thenReturn(Optional.of(tenantScope));
        when(printerAccessService.getPrinters()).thenReturn(List.of());

        recoveryService.recover();

        verify(printerCatalogSyncService).synchronize();
    }

    @Test
    void skipsRecoveryWhenNoLegacyMigrationTenantIsConfigured() {
        when(compatibilityScope.openIfConfigured(anyString())).thenReturn(Optional.empty());

        recoveryService.recover();

        verifyNoInteractions(printerCatalogSyncService, printerAccessService, printerService);
    }
}
