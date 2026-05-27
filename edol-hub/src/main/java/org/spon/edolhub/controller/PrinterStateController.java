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
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final PrinterStatsService printerStatsService;
    private final MaintenanceService maintenanceService;
    private final PrinterDashboardStateService printerDashboardStateService;

    @GetMapping("/state")
    public PrinterStateEnriched getState() {
        return printerDashboardStateService
                .getState();
    }

    @GetMapping("/stats")
    public PrinterStats getStats() {
        return printerStatsService.getStats();
    }

    @GetMapping("/alerts")
    public List<MaintenanceStatusDto> getMaintenanceAlerts() {
        return maintenanceService.getMaintenanceStatus().stream().filter(MaintenanceStatusDto::isDue).toList();
    }

    @Data
    public static class PrinterStateEnriched {
        Long jobId;
        PrinterState printerState;

        boolean allocationPreviewPending;
        PrintAllocationPreviewDto allocationPreview;
    }
}
