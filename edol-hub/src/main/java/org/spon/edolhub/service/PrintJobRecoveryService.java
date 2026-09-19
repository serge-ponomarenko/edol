package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.spool.AllocationPreviewRuntimeSyncService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintJobRecoveryService {

    private final PrinterService printerService;
    private final PrintJobRepository printJobRepository;
    private final PrintRuntimeStateService runtimeStateService;
    private final PrintAllocationPreviewRepository previewRepository;
    private final AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;
    private final PrinterAccessService printerAccessService;
    private final PrinterCatalogSyncService printerCatalogSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        printerCatalogSyncService.synchronize();
        printerAccessService.getPrinters().stream()
                .filter(printer -> printer.isEnabled() && printer.isAvailableInCore())
                .forEach(printer -> recover(printer.getId()));
    }

    private void recover(java.util.UUID printerId) {
        PrinterState state = printerService.getState(printerId);

        if (state == null) {
            log.info("Print job recovery skipped: EDOL Core unavailable");
            return;
        }

        if (!state.isPrinting() || state.getSessionId() == null) {
            log.info("Print job recovery skipped: no active print");
            return;
        }

        printJobRepository.findByPrinterIdAndSessionId(printerId, state.getSessionId())
                .ifPresentOrElse(
                        job -> {
                            runtimeStateService.setCurrentJob(printerId, job);

                            if (previewRepository.existsByPrintJobId(job.getId())) {
                                runtimeStateService.setAllocationPreviewReady(printerId, true);
                                allocationPreviewRuntimeSyncService.refresh(job.getId());

                                log.info(
                                        "Allocation preview recovered. Session ID: {}, Job ID: {}",
                                        state.getSessionId(),
                                        job.getId()
                                );
                            }

                            log.info(
                                    "Print job recovered. Session ID: {}, Job ID: {}",
                                    state.getSessionId(),
                                    job.getId()
                            );
                        },
                        () -> log.warn(
                                "Active print found in Core, but no PrintJob exists for session ID: {}",
                                state.getSessionId()
                        )
                );
    }
}
