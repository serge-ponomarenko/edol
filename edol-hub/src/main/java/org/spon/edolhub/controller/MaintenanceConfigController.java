package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.MaintenanceDefinition;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.MaintenanceDefinitionRepository;
import org.spon.edolhub.service.PrinterStatsService;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/printers/{printerId}/maintenance/config")
@RequiredArgsConstructor
public class MaintenanceConfigController {

    private static final String PRINTER_ID_ATTRIBUTE = "printerId";
    private static final String CONFIG_REDIRECT_PREFIX = "redirect:/printers/";
    private static final String CONFIG_REDIRECT_SUFFIX = "/maintenance/config";

    private final MaintenanceDefinitionRepository repository;
    private final PrinterStatsService printerStatsService;
    private final PrinterAccessService printerAccessService;

    @GetMapping
    public String configPage(@PathVariable UUID printerId, Model model) {
        model.addAttribute(
                "definitions",
                repository.findAllByPrinterId(printerId)
        );
        model.addAttribute(PRINTER_ID_ATTRIBUTE, printerId);

        return "dashboard/maintenance/config";
    }

    @GetMapping("/new")
    public String createForm(@PathVariable UUID printerId, Model model) {

        model.addAttribute("definition", new MaintenanceDefinition());
        model.addAttribute(PRINTER_ID_ATTRIBUTE, printerId);

        return "dashboard/maintenance/config-form";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable UUID printerId, @PathVariable Long id, Model model) {

        MaintenanceDefinition definition = repository.findByIdAndPrinterId(id, printerId).orElseThrow();

        model.addAttribute("definition", definition);
        model.addAttribute(PRINTER_ID_ATTRIBUTE, printerId);

        return "dashboard/maintenance/config-form";
    }

    @PostMapping("/add")
    public String addMaintenance(
            @PathVariable UUID printerId,
            @ModelAttribute MaintenanceDefinition definition) {
        definition.setPrinter(printerAccessService.getPrinter(printerId));
        repository.save(definition);

        return configRedirect(printerId);
    }

    @PostMapping("/{id}/delete")
    public String deleteMaintenance(@PathVariable UUID printerId, @PathVariable Long id) {

        repository.delete(repository.findByIdAndPrinterId(id, printerId).orElseThrow());

        return configRedirect(printerId);
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable UUID printerId, @PathVariable Long id) {
        MaintenanceDefinition m = repository.findByIdAndPrinterId(id, printerId).orElseThrow();

        m.setActive(!m.isActive());

        repository.save(m);

        return configRedirect(printerId);
    }

    @GetMapping("/printer-stats-form")
    public String statsPage(@PathVariable UUID printerId, Model model) {
        model.addAttribute(
                "stats",
                printerStatsService.getStats(printerId)
        );
        model.addAttribute(PRINTER_ID_ATTRIBUTE, printerId);
        return "dashboard/maintenance/printer-stats-form";
    }

    @PostMapping("/printer-stats-form")
    public String updateStats(
            @PathVariable UUID printerId,
            @ModelAttribute PrinterStats stats) {
        printerStatsService.updateStats(printerId, stats);
        return configRedirect(printerId);
    }

    private String configRedirect(UUID printerId) {
        return CONFIG_REDIRECT_PREFIX + printerId + CONFIG_REDIRECT_SUFFIX;
    }

}
