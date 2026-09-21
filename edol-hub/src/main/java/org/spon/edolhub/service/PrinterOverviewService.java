package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.controller.PrinterStateController.PrinterStateEnriched;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.dto.PrinterOverviewDto;
import org.spon.edolhub.model.entity.Printer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterOverviewService {

    private final PrinterAccessService printerAccessService;
    private final PrinterService printerService;
    private final PrinterDashboardStateService printerDashboardStateService;
    private final MaintenanceService maintenanceService;

    public List<PrinterOverviewDto> getOverview() {
        CoreCatalog coreCatalog = getCoreCatalog();

        return printerAccessService.getPrinters().stream()
                .map(printer -> toOverview(printer, coreCatalog))
                .toList();
    }

    private CoreCatalog getCoreCatalog() {
        try {
            Map<UUID, CorePrinterDto> printers = printerService.getPrinters().stream()
                    .collect(Collectors.toMap(CorePrinterDto::printerId, Function.identity()));
            return new CoreCatalog(printers, true);
        } catch (RuntimeException exception) {
            log.warn("Cannot load EDOL Core printer catalog for overview");
            return new CoreCatalog(Map.of(), false);
        }
    }

    private PrinterOverviewDto toOverview(Printer printer, CoreCatalog coreCatalog) {
        CorePrinterDto corePrinter = coreCatalog.printers().get(printer.getId());
        boolean availableInCore = coreCatalog.loaded()
                ? corePrinter != null
                : printer.isAvailableInCore();
        PrinterStateEnriched dashboardState = getDashboardState(printer, availableInCore);

        return new PrinterOverviewDto(
                printer.getId(),
                printer.getDisplayId(),
                printer.getName(),
                corePrinter == null ? null : corePrinter.model(),
                printer.isEnabled(),
                availableInCore,
                dashboardState == null ? null : dashboardState.getPrinterState(),
                dashboardState == null ? null : dashboardState.getJobId(),
                getMaintenanceAlertCount(printer.getId())
        );
    }

    private PrinterStateEnriched getDashboardState(Printer printer, boolean availableInCore) {
        if (!printer.isEnabled() || !availableInCore) {
            return null;
        }

        try {
            return printerDashboardStateService.getState(printer.getId());
        } catch (RuntimeException exception) {
            log.warn("Cannot load dashboard state for printer {}", printer.getId());
            return null;
        }
    }

    private int getMaintenanceAlertCount(UUID printerId) {
        try {
            return (int) maintenanceService.getMaintenanceStatus(printerId).stream()
                    .filter(alert -> alert.isDue())
                    .count();
        } catch (RuntimeException exception) {
            log.warn("Cannot load maintenance alerts for printer {}", printerId);
            return 0;
        }
    }

    private record CoreCatalog(Map<UUID, CorePrinterDto> printers, boolean loaded) {
    }
}
