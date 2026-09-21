package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.PrinterOverviewDto;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterCatalogStatus;
import org.spon.edolhub.service.PrinterOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/printers")
@RequiredArgsConstructor
public class PrinterCatalogController {

    private final PrinterAccessService printerAccessService;
    private final PrinterCatalogStatus status;
    private final PrinterOverviewService printerOverviewService;

    @GetMapping
    public List<PrinterSummary> getPrinters() {
        return printerAccessService.getPrinters().stream()
                .map(PrinterSummary::from)
                .toList();
    }

    @GetMapping("/overview")
    public List<PrinterOverviewDto> getOverview() {
        return printerOverviewService.getOverview();
    }

    @GetMapping("/catalog-status")
    public CatalogStatus getStatus() {
        return new CatalogStatus(
                status.isCoreAvailable(),
                status.isMigrationBlocked(),
                status.getMessage(),
                status.getLastSuccessfulSync()
        );
    }

    public record PrinterSummary(
            UUID id,
            String displayId,
            String name,
            boolean enabled,
            boolean availableInCore
    ) {
        private static PrinterSummary from(Printer printer) {
            return new PrinterSummary(
                    printer.getId(),
                    printer.getDisplayId(),
                    printer.getName(),
                    printer.isEnabled(),
                    printer.isAvailableInCore()
            );
        }
    }

    public record CatalogStatus(
            boolean coreAvailable,
            boolean migrationBlocked,
            String message,
            Instant lastSuccessfulSync
    ) {
    }
}
