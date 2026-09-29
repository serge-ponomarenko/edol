package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.PrinterRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterCatalogSyncService {

    private final PrinterService printerService;
    private final PrinterRepository printerRepository;
    private final TenantContext tenantContext;
    private final LegacyPrinterBackfillService backfillService;
    private final PrinterCatalogStatus status;
    private final TenantScopeProvider tenantScopeProvider;
    private final TenantAwareTransactionalExecutor transactionalExecutor;
    private final ReentrantLock synchronizationLock = new ReentrantLock();

    @Scheduled(
            fixedDelayString = "${edol-hub.printer-sync-interval-ms:60000}",
            initialDelayString = "${edol-hub.printer-sync-interval-ms:60000}"
    )
    public void synchronize() {
        var scope = tenantScopeProvider.openIfConfigured("scheduled-printer-catalog-sync");
        if (scope.isEmpty()) {
            status.migrationBlocked("Legacy tenant compatibility is not configured");
            return;
        }
        try (TenantContext.TenantScope ignored = scope.get()) {
            synchronizationLock.lock();
            try {
                try {
                    transactionalExecutor.executeInCurrentTenantTransaction(this::synchronizeCurrentTenant);
                    status.synchronizedSuccessfully();
                } catch (IllegalStateException e) {
                    status.migrationBlocked(e.getMessage());
                    log.error("Printer catalog migration is blocked", e);
                } catch (Exception e) {
                    status.coreUnavailable("EDOL Core printer catalog is unavailable");
                    log.warn("Cannot synchronize EDOL Core printer catalog", e);
                }
            } finally {
                synchronizationLock.unlock();
            }
        }
    }

    private void synchronizeCurrentTenant() {
        List<CorePrinterDto> corePrinters = printerService.getPrinters();
        backfillService.validateCoreCatalog(corePrinters);
        UUID tenantId = tenantContext.getCurrentTenantId();
        Set<UUID> corePrinterIds = corePrinters.stream()
                .map(CorePrinterDto::printerId)
                .collect(Collectors.toSet());

        List<Printer> existingPrinters = printerRepository.findAllByTenantIdOrderByDisplayId(tenantId);
        existingPrinters.forEach(printer -> printer.setAvailableInCore(corePrinterIds.contains(printer.getId())));
        printerRepository.saveAll(existingPrinters);

        corePrinters.forEach(dto -> upsert(dto, tenantId));
        backfillService.validateOwnership(corePrinters);
    }

    @Transactional
    public Printer synchronize(UUID printerId) {
        synchronizationLock.lock();
        try {
            Printer existing = printerRepository.findById(printerId).orElse(null);
            try {
                CorePrinterDto dto = printerService.getPrinter(printerId);
                if (dto == null) {
                    throw new IllegalStateException("Printer is missing from EDOL Core: " + printerId);
                }
                return upsert(dto, existing == null ? tenantContext.getCurrentTenantId() : existing.getTenantId());
            } catch (Exception e) {
                status.coreUnavailable("EDOL Core printer catalog is unavailable");
                throw e;
            }
        } finally {
            synchronizationLock.unlock();
        }
    }

    private Printer upsert(CorePrinterDto dto, UUID tenantId) {
        Printer printer = printerRepository.findById(dto.printerId())
                .orElseGet(Printer::new);
        printer.setId(dto.printerId());
        if (printer.getTenantId() == null) {
            printer.setTenantId(tenantId);
        }
        printer.setDisplayId(dto.displayId());
        printer.setName(dto.name());
        printer.setEnabled(dto.enabled());
        printer.setAvailableInCore(true);
        printer.setLastSyncedAt(Instant.now());
        return printerRepository.save(printer);
    }
}
