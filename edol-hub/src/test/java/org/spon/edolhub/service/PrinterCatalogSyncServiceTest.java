package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.repository.PrinterRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterCatalogSyncServiceTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private PrinterService printerService;

    @Mock
    private PrinterRepository printerRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private LegacyPrinterBackfillService backfillService;

    @Mock
    private PrinterCatalogStatus status;

    @InjectMocks
    private PrinterCatalogSyncService syncService;

    @Test
    void serializesCatalogAndMqttPrinterSynchronization() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        CorePrinterDto printer = new CorePrinterDto(PRINTER_ID, "P1", "Printer", true);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger activeSaves = new AtomicInteger();
        AtomicInteger maximumConcurrentSaves = new AtomicInteger();

        when(printerService.getPrinters()).thenReturn(List.of(printer));
        when(printerService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(tenantContext.getCurrentTenant()).thenReturn(tenant);
        when(printerRepository.findAllByTenantIdOrderByDisplayId(TENANT_ID)).thenReturn(List.of());
        when(printerRepository.findById(PRINTER_ID)).thenReturn(Optional.empty());
        when(printerRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(printerRepository.save(any(Printer.class))).thenAnswer(invocation -> {
            int concurrentSaves = activeSaves.incrementAndGet();
            maximumConcurrentSaves.accumulateAndGet(concurrentSaves, Math::max);
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } finally {
                activeSaves.decrementAndGet();
            }
            return invocation.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> catalogSync = executor.submit(() -> awaitAndSynchronize(start, () -> syncService.synchronize()));
            Future<?> mqttSync = executor.submit(() -> awaitAndSynchronize(start, () -> syncService.synchronize(PRINTER_ID)));

            start.countDown();
            catalogSync.get(5, TimeUnit.SECONDS);
            mqttSync.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(maximumConcurrentSaves.get()).isEqualTo(1);
    }

    private void awaitAndSynchronize(CountDownLatch start, Runnable synchronization) {
        try {
            start.await();
            synchronization.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
