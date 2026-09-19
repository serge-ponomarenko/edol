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
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.spool.AllocationMutationService;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.spon.edolhub.service.spool.PrintAllocationReconciliationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.spon.edolhub.config.GramUtils.GRAM_EPSILON;

@Controller
@RequiredArgsConstructor
public class PrintAllocationController {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintAllocationPreviewMapper previewMapper;
    private final AllocationMutationService allocationMutationService;
    private final PrintAllocationReconciliationService printAllocationReconciliationService;
    private final FilamentRepository filamentRepository;
    private final FilamentSpoolRepository filamentSpoolRepository;
    private final PrintJobRepository printJobRepository;
    private final PrinterAccessService printerAccessService;
    private final TenantContext tenantContext;

    @GetMapping("/printers/{printerId}/print-jobs/allocation/{jobId}")
    public String allocationPage(
            @PathVariable UUID printerId,
            @PathVariable Long jobId,
            Model model
    ) {
        resolveJobId(printerId, jobId);
        model.addAttribute(
                "currentPath",
                "/printers/" + printerId + "/print-jobs"
        );

        model.addAttribute(
                "jobId",
                jobId
        );
        model.addAttribute("printerId", printerId);

        return "dashboard/print-jobs/allocation";

    }

    @GetMapping("/api/printers/{printerId}/allocations/job/{jobId}")
    @ResponseBody
    public PrintAllocationPreviewDto getAllocation(
            @PathVariable UUID printerId,
            @PathVariable Long jobId
    ) {
        UUID printJobId = resolveJobId(printerId, jobId);

        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(printJobId)
                        .orElseThrow();

        return previewMapper.toDto(preview);
    }

    @PostMapping("/api/printers/{printerId}/allocations/rerun")
    @ResponseBody
    public void rerunAllocation(
            @PathVariable UUID printerId,
            @RequestParam Long jobId,
            @RequestParam Long filamentId
    ) {
        allocationMutationService.rerunAllocation(
                resolveJobId(printerId, jobId),
                filamentId
        );
    }

    @PostMapping("/api/printers/{printerId}/allocations/finalize")
    @ResponseBody
    public Boolean finalizeReconciliation(
            @PathVariable UUID printerId,
            @RequestParam Long jobId
    ) {
        return printAllocationReconciliationService.finalizeReconciliation(
                resolveJobId(printerId, jobId)
        );
    }

    @GetMapping("/api/printers/{printerId}/allocations/filaments")
    @ResponseBody
    public List<Filament> filaments(
            @PathVariable UUID printerId,
            @RequestParam("query")
            String query
    ) {
        printerAccessService.getPrinter(printerId);
        return filamentRepository
                .findAllByTenantIdOrderByFullId(tenantContext.getCurrentTenantId())
                .stream()
                .filter(f ->
                        f.getFullId() != null
                                && f.getFullId()
                                .toLowerCase()
                                .contains(
                                        query.toLowerCase()
                                )
                )
                //.limit(20)
                .toList();
    }

    @PostMapping("/api/printers/{printerId}/allocations/replace-filament")
    @ResponseBody
    public void replaceFilament(
            @PathVariable UUID printerId,
            @RequestParam Long jobId,
            @RequestParam Long sourceFilamentId,
            @RequestParam Long targetFilamentId
    ) {
        Filament filament =
                filamentRepository
                        .findByIdAndTenantId(targetFilamentId, tenantContext.getCurrentTenantId())
                        .orElseThrow();

        allocationMutationService
                .replaceFilament(
                        resolveJobId(printerId, jobId),
                        sourceFilamentId,
                        filament
                );

    }

    @GetMapping("/api/printers/{printerId}/allocations/spools")
    @ResponseBody
    public List<AllocationSpoolOptionDto> spools(
            @PathVariable UUID printerId,
            @RequestParam Long filamentId
    ) {
        printerAccessService.getPrinter(printerId);
        return filamentSpoolRepository
                .findAllByFilamentIdAndFilamentTenantIdAndStatusIn(
                        filamentId,
                        tenantContext.getCurrentTenantId(),
                        List.of(
                                FilamentSpool.FilamentSpoolStatus.ACTIVE,
                                FilamentSpool.FilamentSpoolStatus.SEALED
                        )
                )
                .stream()
                .map(this::toSpoolOptionDto)
                .toList();
    }

    @PostMapping("/api/printers/{printerId}/allocations/replace-spool")
    @ResponseBody
    public void replaceSpool(
            @PathVariable UUID printerId,
            @RequestParam Long jobId,
            @RequestParam Long filamentId,
            @RequestParam Long spoolId,
            @RequestParam Double grams
    ) {
        FilamentSpool spool =
                filamentSpoolRepository
                        .findByIdAndFilamentTenantId(spoolId, tenantContext.getCurrentTenantId())
                        .orElseThrow();

        validateSpoolMutation(
                filamentId,
                spool,
                grams
        );

        allocationMutationService
                .replaceAllocationWithSingleSpool(
                        resolveJobId(printerId, jobId),
                        filamentId,
                        spool,
                        grams,
                        calculateCost(
                                spool,
                                grams
                        )
                );
    }

    @PostMapping("/api/printers/{printerId}/allocations/add-spool")
    @ResponseBody
    public void addSpool(
            @PathVariable UUID printerId,
            @RequestParam Long jobId,
            @RequestParam Long filamentId,
            @RequestParam Long spoolId,
            @RequestParam Double grams
    ) {
        FilamentSpool spool =
                filamentSpoolRepository
                        .findByIdAndFilamentTenantId(spoolId, tenantContext.getCurrentTenantId())
                        .orElseThrow();

        validateSpoolMutation(
                filamentId,
                spool,
                grams
        );

        allocationMutationService
                .addAllocationItem(
                        resolveJobId(printerId, jobId),
                        filamentId,
                        spool,
                        grams,
                        calculateCost(
                                spool,
                                grams
                        )
                );
    }

    private UUID resolveJobId(UUID printerId, Long publicId) {
        printerAccessService.getPrinter(printerId);
        var job = printJobRepository
                .findByPublicIdAndPrinterTenantId(publicId, tenantContext.getCurrentTenantId())
                .orElseThrow();
        if (job.getPrinter() == null || !printerId.equals(job.getPrinter().getId())) {
            throw new IllegalArgumentException("Print job does not belong to the selected printer");
        }
        return job.getId();
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
            Double grams
    ) {
        if (
                grams == null
                        || grams <= GRAM_EPSILON
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

        Double available =
                spool.getWeightRemaining() != null
                        ? spool.getWeightRemaining()
                        : spool.getWeightTotal();

        if (
                available != null
                        && grams - available > GRAM_EPSILON
        ) {
            throw new IllegalArgumentException(
                    "Allocation exceeds spool remaining weight"
            );
        }
    }

    private BigDecimal calculateCost(
            FilamentSpool spool,
            Double grams
    ) {
        if (
                grams == null
                        || grams <= GRAM_EPSILON
                        || spool.getPrice() == null
                        || spool.getWeightTotal() == null
                        || spool.getWeightTotal() <= GRAM_EPSILON
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
