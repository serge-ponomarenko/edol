package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.spon.edolhub.service.spool.PrintAllocationReconciliationService;
import org.spon.edolhub.service.spool.SpoolAllocationOverrideService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PrintAllocationController {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintAllocationPreviewMapper previewMapper;
    private final SpoolAllocationOverrideService spoolAllocationOverrideService;
    private final PrintAllocationReconciliationService printAllocationReconciliationService;
    private final FilamentRepository filamentRepository;

    @GetMapping("/print-jobs/allocation/{jobId}")
    public String allocationPage(
            @PathVariable Long jobId,
            Model model
    ) {
        model.addAttribute(
                "currentPath",
                "/print-jobs"
        );

        model.addAttribute(
                "jobId",
                jobId
        );

        return "dashboard/print-jobs/allocation";

    }

    @GetMapping("/api/allocations/job/{jobId}")
    @ResponseBody
    public PrintAllocationPreviewDto getAllocation(
            @PathVariable Long jobId
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(jobId)
                        .orElseThrow();

        return previewMapper.toDto(
                preview
        );
    }

    @PostMapping("/api/allocations/rerun")
    @ResponseBody
    public void rerunAllocation(
            @RequestParam Long jobId,
            @RequestParam Long filamentId
    ) {
        spoolAllocationOverrideService
                .rerunAllocation(
                        jobId,
                        filamentId
                );
    }

    @PostMapping("/api/allocations/finalize")
    @ResponseBody
    public Boolean finalizeReconciliation(
            @RequestParam Long jobId
    ) {

        return printAllocationReconciliationService
                .finalizeReconciliation(jobId);

    }

    @GetMapping("/api/allocations/filaments")
    @ResponseBody
    public List<Filament> filaments(
            @RequestParam("query")
            String query
    ) {
        return filamentRepository
                .findAll()
                .stream()
                .filter(f ->
                        f.getFullId() != null
                                && f.getFullId()
                                .toLowerCase()
                                .contains(
                                        query.toLowerCase()
                                )
                )
                .limit(20)
                .toList();
    }

    @PostMapping("/api/allocations/replace-filament")
    @ResponseBody
    public void replaceFilament(
            @RequestParam Long jobId,
            @RequestParam Long sourceFilamentId,
            @RequestParam Long targetFilamentId
    ) {
        Filament filament =
                filamentRepository
                        .findById(
                                targetFilamentId
                        )
                        .orElseThrow();

        spoolAllocationOverrideService
                .replaceFilament(
                        jobId,
                        sourceFilamentId,
                        filament
                );

    }

}