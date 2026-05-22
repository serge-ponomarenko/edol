package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.MaintenanceStatusDto;
import org.spon.edolhub.service.MaintenanceService;
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final PrinterStatsService printerStatsService;

    @GetMapping("/maintenance")
    public String maintenancePage(Model model) {

        model.addAttribute(
                "maintenances",
                maintenanceService.getMaintenanceStatus()
        );

        model.addAttribute(
                "stats",
                printerStatsService.getStats()
        );

        long dueCount = maintenanceService
                .getMaintenanceStatus()
                .stream()
                .filter(MaintenanceStatusDto::isDue)
                .count();

        model.addAttribute("dueCount", dueCount);

        return "dashboard/maintenance/list";
    }

    @PostMapping("/maintenance/{id}/complete")
    public String completeMaintenance(
            @PathVariable Long id,
            @RequestParam(required = false) String notes) {

        maintenanceService.completeMaintenance(id, notes);

        return "redirect:/maintenance";
    }
}