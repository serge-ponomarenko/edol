package org.spon.edolhub.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.MaintenanceStatusDto;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.service.MaintenanceService;
import org.spon.edolhub.service.PrinterDashboardStateService;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final PrinterStatsService printerStatsService;
    private final MaintenanceService maintenanceService;
    private final PrinterDashboardStateService printerDashboardStateService;
    private final PrinterAccessService printerAccessService;

    @GetMapping("/printers/{printerId}/state")
    public PrinterStateEnriched getState(@PathVariable UUID printerId) {
        printerAccessService.getPrinter(printerId);
        return printerDashboardStateService
                .getState(printerId);
    }

    @GetMapping("/printers/{printerId}/stats")
    public PrinterStats getStats(@PathVariable UUID printerId) {
        return printerStatsService.getStats(printerId);
    }

    @GetMapping("/printers/{printerId}/alerts")
    public List<MaintenanceStatusDto> getMaintenanceAlerts(@PathVariable UUID printerId) {
        return maintenanceService.getMaintenanceStatus(printerId).stream()
                .filter(MaintenanceStatusDto::isDue)
                .toList();
    }

    @Deprecated(forRemoval = true)
    @GetMapping("/printer/state")
    public PrinterStateEnriched getDefaultState() {
        return getState(printerAccessService.getDefaultPrinter().getId());
    }

    @Deprecated(forRemoval = true)
    @GetMapping("/printer/stats")
    public PrinterStats getDefaultStats() {
        return getStats(printerAccessService.getDefaultPrinter().getId());
    }

    @Deprecated(forRemoval = true)
    @GetMapping("/printer/alerts")
    public List<MaintenanceStatusDto> getDefaultMaintenanceAlerts() {
        return getMaintenanceAlerts(printerAccessService.getDefaultPrinter().getId());
    }

    @Data
    public static class PrinterStateEnriched {
        Long jobId;
        PrinterState printerState;

        boolean allocationPreviewPending;
        PrintAllocationPreviewDto allocationPreview;
    }
}
