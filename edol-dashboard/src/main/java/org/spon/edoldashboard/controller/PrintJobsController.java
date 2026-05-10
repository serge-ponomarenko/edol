package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.JobFilamentUsage;
import org.spon.edoldashboard.model.entity.PrintJob;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.PrintJobRepository;
import org.spon.edoldashboard.service.PrintJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/print-jobs")
public class PrintJobsController {

    private final PrintJobRepository printJobRepository;
    private final PrintJobService printJobService;
    private final FilamentRepository filamentRepository;

    @GetMapping
    public String list(Model model) {
        return "dashboard/print-jobs/list";
    }

    @PostMapping
    public String save(@ModelAttribute PrintJob formJob) {
        PrintJob existing = printJobRepository.findById(formJob.getId())
                .orElseThrow();

        for (int i = 0; i < existing.getJobFilamentUsages().size(); i++) {
            JobFilamentUsage existingUsage = existing.getJobFilamentUsages().get(i);
            JobFilamentUsage formUsage = formJob.getJobFilamentUsages().get(i);

            boolean changed = !existingUsage.getFilament().getId()
                    .equals(formUsage.getFilament().getId());

            if (changed) {
                existingUsage.setFilament(
                        filamentRepository.findById(formUsage.getFilament().getId())
                                .orElseThrow()
                );
                printJobService.recalculateCost(formJob.getId());
            }
        }

        printJobRepository.save(existing);

        return "redirect:/print-jobs";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        PrintJob printJob = printJobRepository.findById(id).orElseThrow();
        model.addAttribute("printjob", printJob);
        model.addAttribute("filaments", filamentRepository.findAll());
        return "dashboard/print-jobs/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        printJobRepository.deleteById(id);
        return "redirect:/print-jobs";
    }

    @PostMapping("/recalculate-cost/{id}")
    public ResponseEntity<Void> recalculateCost(@PathVariable Long id) {
        printJobService.recalculateCost(id);
        return ResponseEntity.ok().build();
    }

}
