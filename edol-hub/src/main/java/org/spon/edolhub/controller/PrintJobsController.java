package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/printers/{printerId}/print-jobs")
public class PrintJobsController {

    private final PrintJobRepository printJobRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public String list(@PathVariable UUID printerId, Model model) {
        model.addAttribute("printerId", printerId);
        return "dashboard/print-jobs/list";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable UUID printerId, @PathVariable Long id) {
        PrintJob job = printJobRepository
                .findByPublicIdAndPrinterTenantId(id, tenantContext.getCurrentTenantId())
                .filter(candidate -> candidate.getPrinter().getId().equals(printerId))
                .orElseThrow();

        printJobRepository.delete(job);

        return "redirect:/printers/" + printerId + "/print-jobs";
    }

}
