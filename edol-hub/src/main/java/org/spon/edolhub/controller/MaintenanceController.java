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

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final PrinterStatsService printerStatsService;

    @GetMapping("/printers/{printerId}/maintenance")
    public String maintenancePage(@PathVariable UUID printerId, Model model) {

        model.addAttribute(
                "maintenances",
                maintenanceService.getMaintenanceStatus(printerId)
        );

        model.addAttribute(
                "stats",
                printerStatsService.getStats(printerId)
        );

        long dueCount = maintenanceService
                .getMaintenanceStatus(printerId)
                .stream()
                .filter(MaintenanceStatusDto::isDue)
                .count();

        model.addAttribute("dueCount", dueCount);
        model.addAttribute("printerId", printerId);

        return "dashboard/maintenance/list";
    }

    @PostMapping("/printers/{printerId}/maintenance/{id}/complete")
    public String completeMaintenance(
            @PathVariable UUID printerId,
            @PathVariable Long id,
            @RequestParam(required = false) String notes) {

        maintenanceService.completeMaintenance(printerId, id, notes);

        return "redirect:/printers/" + printerId + "/maintenance";
    }
}
