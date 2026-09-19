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

    public PrinterStats getStats(UUID printerId) {
        Printer printer = printerAccessService.getPrinter(printerId);
        PrinterStats stats = repository.findByPrinterId(printerId)
                .orElseGet(() -> createStats(printer));

        stats.setTotalPrintHours(stats.getTotalPrintSeconds() / 3600);
        return stats;
    }

    public PrinterStats getStats() {
        return getStats(printerAccessService.getDefaultPrinter().getId());
    }

    private PrinterStats createStats(Printer printer) {
        PrinterStats stats = new PrinterStats();
        stats.setPrinter(printer);
        stats.setUpdatedAt(LocalDateTime.now());

        return repository.save(stats);
    }

    public int getTotalPrinterHours(UUID printerId) {
        PrinterStats stats = getStats(printerId);

        return (int) (stats.getTotalPrintSeconds() / 3600);
    }

    public void addPrintJob(Printer printer, long jobSeconds, long filamentGrams) {
        PrinterStats stats = getStats(printer.getId());

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
        PrinterStats stats = getStats(printerId);

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
