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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private PrintJobRecoveryService recoveryService;

    @Test
    void synchronizesCatalogOnceBeforeRecoveringPrinters() {
        when(printerAccessService.getPrinters()).thenReturn(List.of());

        recoveryService.recover();

        verify(printerCatalogSyncService).synchronize();
    }
}
