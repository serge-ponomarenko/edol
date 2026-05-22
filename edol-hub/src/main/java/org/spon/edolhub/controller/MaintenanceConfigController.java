package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.MaintenanceDefinition;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.MaintenanceDefinitionRepository;
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/maintenance/config")
@RequiredArgsConstructor
public class MaintenanceConfigController {

    private final MaintenanceDefinitionRepository repository;
    private final PrinterStatsService printerStatsService;

    @GetMapping
    public String configPage(Model model) {
        model.addAttribute(
                "definitions",
                repository.findAll()
        );

        return "dashboard/maintenance/config";
    }

    @GetMapping("/new")
    public String createForm(Model model) {

        model.addAttribute("definition", new MaintenanceDefinition());

        return "dashboard/maintenance/config-form";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {

        MaintenanceDefinition definition = repository.findById(id).orElseThrow();

        model.addAttribute("definition", definition);

        return "dashboard/maintenance/config-form";
    }

    @PostMapping("/add")
    public String addMaintenance(
            @ModelAttribute MaintenanceDefinition definition) {
        repository.save(definition);

        return "redirect:/maintenance/config";
    }

    @PostMapping("/{id}/delete")
    public String deleteMaintenance(@PathVariable Long id) {

        repository.deleteById(id);

        return "redirect:/maintenance/config";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        MaintenanceDefinition m = repository.findById(id).orElseThrow();

        m.setActive(!m.isActive());

        repository.save(m);

        return "redirect:/maintenance/config";
    }

    @GetMapping("/printer-stats-form")
    public String statsPage(Model model) {
        model.addAttribute(
                "stats",
                printerStatsService.getStats()
        );
        return "dashboard/maintenance/printer-stats-form";
    }

    @PostMapping("/printer-stats-form")
    public String updateStats(
            @ModelAttribute PrinterStats stats) {
        printerStatsService.updateStats(stats);
        return "redirect:/maintenance/config";
    }

}