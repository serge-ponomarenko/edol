package org.spon.edolhub.model.dto;

import org.spon.edolhub.model.entity.PrinterStats;

import java.time.LocalDateTime;

public record PrinterStatsDto(
        Long id,
        Long totalPrintSeconds,
        Long totalJobs,
        Long totalFilamentUsedGrams,
        LocalDateTime printerStartedPrintingDate,
        LocalDateTime updatedAt,
        Long totalPrintHours
) {

    public static PrinterStatsDto from(PrinterStats stats) {
        return new PrinterStatsDto(
                stats.getId(),
                stats.getTotalPrintSeconds(),
                stats.getTotalJobs(),
                stats.getTotalFilamentUsedGrams(),
                stats.getPrinterStartedPrintingDate(),
                stats.getUpdatedAt(),
                stats.getTotalPrintHours()
        );
    }
}
