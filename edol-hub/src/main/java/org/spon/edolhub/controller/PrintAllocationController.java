package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationSpoolOptionDto;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.spool.AllocationMutationService;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.spon.edolhub.service.spool.PrintAllocationReconciliationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PrintAllocationController {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintAllocationPreviewMapper previewMapper;
    private final AllocationMutationService allocationMutationService;
    private final PrintAllocationReconciliationService printAllocationReconciliationService;
    private final FilamentRepository filamentRepository;
    private final FilamentSpoolRepository filamentSpoolRepository;

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
        allocationMutationService
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

        allocationMutationService
                .replaceFilament(
                        jobId,
                        sourceFilamentId,
                        filament
                );

    }

    @GetMapping("/api/allocations/spools")
    @ResponseBody
    public List<AllocationSpoolOptionDto> spools(
            @RequestParam Long filamentId
    ) {
        return filamentSpoolRepository
                .findAllByFilamentIdAndStatusIn(
                        filamentId,
                        List.of(
                                FilamentSpool.FilamentSpoolStatus.ACTIVE,
                                FilamentSpool.FilamentSpoolStatus.SEALED
                        )
                )
                .stream()
                .map(this::toSpoolOptionDto)
                .toList();
    }

    @PostMapping("/api/allocations/replace-spool")
    @ResponseBody
    public void replaceSpool(
            @RequestParam Long jobId,
            @RequestParam Long filamentId,
            @RequestParam Long spoolId,
            @RequestParam Integer grams
    ) {
        FilamentSpool spool =
                filamentSpoolRepository
                        .findById(spoolId)
                        .orElseThrow();

        validateSpoolMutation(
                filamentId,
                spool,
                grams
        );

        allocationMutationService
                .replaceAllocationWithSingleSpool(
                        jobId,
                        filamentId,
                        spool,
                        grams,
                        calculateCost(
                                spool,
                                grams
                        )
                );
    }

    @PostMapping("/api/allocations/add-spool")
    @ResponseBody
    public void addSpool(
            @RequestParam Long jobId,
            @RequestParam Long filamentId,
            @RequestParam Long spoolId,
            @RequestParam Integer grams
    ) {
        FilamentSpool spool =
                filamentSpoolRepository
                        .findById(spoolId)
                        .orElseThrow();

        validateSpoolMutation(
                filamentId,
                spool,
                grams
        );

        allocationMutationService
                .addAllocationItem(
                        jobId,
                        filamentId,
                        spool,
                        grams,
                        calculateCost(
                                spool,
                                grams
                        )
                );
    }

    private AllocationSpoolOptionDto toSpoolOptionDto(
            FilamentSpool spool
    ) {
        AllocationSpoolOptionDto dto =
                new AllocationSpoolOptionDto();

        dto.setId(spool.getId());
        dto.setName(spool.getDisplayName());
        dto.setStatus(spool.getStatus().name());
        dto.setWeightTotal(spool.getWeightTotal());
        dto.setWeightRemaining(spool.getWeightRemaining());
        dto.setPrice(spool.getPrice());

        return dto;
    }

    private void validateSpoolMutation(
            Long filamentId,
            FilamentSpool spool,
            Integer grams
    ) {
        if (
                grams == null
                        || grams <= 0
        ) {
            throw new IllegalArgumentException(
                    "Allocation grams must be greater than zero"
            );
        }

        if (
                spool.getFilament() == null
                        || !spool.getFilament()
                        .getId()
                        .equals(filamentId)
        ) {
            throw new IllegalArgumentException(
                    "Selected spool does not match allocation filament"
            );
        }

        Integer available =
                spool.getWeightRemaining() != null
                        ? spool.getWeightRemaining()
                        : spool.getWeightTotal();

        if (
                available != null
                        && grams > available
        ) {
            throw new IllegalArgumentException(
                    "Allocation exceeds spool remaining weight"
            );
        }
    }

    private BigDecimal calculateCost(
            FilamentSpool spool,
            Integer grams
    ) {
        if (
                grams == null
                        || grams <= 0
                        || spool.getPrice() == null
                        || spool.getWeightTotal() == null
                        || spool.getWeightTotal() <= 0
        ) {
            return BigDecimal.ZERO;
        }

        return spool.getPrice()
                .divide(
                        BigDecimal.valueOf(
                                spool.getWeightTotal()
                        ),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        BigDecimal.valueOf(grams)
                );
    }

}
