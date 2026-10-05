package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.PrinterStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PrinterStatsService {

    private final PrinterStatsRepository repository;
    private final PrinterAccessService printerAccessService;

    @Transactional(readOnly = true)
    public PrinterStats getStats(UUID printerId) {
        Printer printer = printerAccessService.getPrinter(printerId);
        PrinterStats stats = repository.findByPrinterId(printerId)
                .orElseGet(() -> zeroStats(printer));

        stats.setTotalPrintHours(stats.getTotalPrintSeconds() / 3600);
        return stats;
    }

    @Transactional(readOnly = true)
    public PrinterStats getStats() {
        return getStats(printerAccessService.getDefaultPrinter().getId());
    }

    public void initializeStats(Printer printer) {
        repository.findByPrinterId(printer.getId())
                .orElseGet(() -> repository.save(newPersistedStats(printer)));
    }

    private PrinterStats getOrCreateStats(UUID printerId) {
        Printer printer = printerAccessService.getPrinter(printerId);
        return repository.findByPrinterId(printerId)
                .orElseGet(() -> repository.save(newPersistedStats(printer)));
    }

    private PrinterStats zeroStats(Printer printer) {
        PrinterStats stats = new PrinterStats();
        stats.setPrinter(printer);
        return stats;
    }

    private PrinterStats newPersistedStats(Printer printer) {
        PrinterStats stats = zeroStats(printer);
        stats.setUpdatedAt(LocalDateTime.now());
        return stats;
    }

    public int getTotalPrinterHours(UUID printerId) {
        PrinterStats stats = getStats(printerId);

        return (int) (stats.getTotalPrintSeconds() / 3600);
    }

    public void addPrintJob(Printer printer, long jobSeconds, long filamentGrams) {
        PrinterStats stats = getOrCreateStats(printer.getId());

        stats.setTotalPrintSeconds(
                stats.getTotalPrintSeconds() + jobSeconds
        );

        stats.setTotalJobs(
                stats.getTotalJobs() + 1
        );

        stats.setTotalFilamentUsedGrams(
                stats.getTotalFilamentUsedGrams() + filamentGrams
        );

        stats.setUpdatedAt(LocalDateTime.now());

        repository.save(stats);
    }

    @Transactional
    public void updateStats(UUID printerId, PrinterStats updated) {
        PrinterStats stats = getOrCreateStats(printerId);

        if (updated.getTotalPrintHours() != null) {

            stats.setTotalPrintSeconds(
                    updated.getTotalPrintHours() * 3600
            );

        }

        stats.setPrinterStartedPrintingDate(updated.getPrinterStartedPrintingDate());

        stats.setTotalJobs(updated.getTotalJobs());
        stats.setTotalFilamentUsedGrams(updated.getTotalFilamentUsedGrams());
        stats.setUpdatedAt(LocalDateTime.now());

        repository.save(stats);
    }

    public double getTotalPrinterHoursDouble(UUID printerId) {
        return getStats(printerId).getTotalPrintSeconds() / 3600.0;
    }

    public String getFormattedPrinterTime(UUID printerId) {

        long seconds = getStats(printerId).getTotalPrintSeconds();

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return hours + "h " + minutes + "m";
    }
}
