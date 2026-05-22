package org.spon.edolhub.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.PrinterStatsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PrinterStatsService {

    private final PrinterStatsRepository repository;

    @PostConstruct
    public void init() {
        getStats();
    }

    public PrinterStats getStats() {
        PrinterStats stats = repository.findAll()
                .stream()
                .findFirst()
                .orElseGet(this::createStats);

        stats.setTotalPrintHours(
                stats.getTotalPrintSeconds() / 3600
        );

        return stats;
    }

    private PrinterStats createStats() {
        PrinterStats stats = new PrinterStats();
        stats.setUpdatedAt(LocalDateTime.now());

        return repository.save(stats);
    }

    public int getTotalPrinterHours() {
        PrinterStats stats = getStats();

        return (int) (stats.getTotalPrintSeconds() / 3600);
    }

    public void addPrintJob(long jobSeconds, long filamentGrams) {
        PrinterStats stats = getStats();

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
    public void updateStats(PrinterStats updated) {
        PrinterStats stats = getStats();

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

    public double getTotalPrinterHoursDouble() {
        return getStats().getTotalPrintSeconds() / 3600.0;
    }

    public String getFormattedPrinterTime() {

        long seconds = getStats().getTotalPrintSeconds();

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return hours + "h " + minutes + "m";
    }
}
