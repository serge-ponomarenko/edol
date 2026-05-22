package org.spon.edoldashboard.service.filament;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edoldashboard.model.dto.AllocationResult;
import org.spon.edoldashboard.model.dto.AllocationResultDto;
import org.spon.edoldashboard.model.dto.FilamentCostPreviewDto;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.service.spool.SpoolAllocationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrinterFilamentUsageService {

    private final FilamentMatchingService filamentMatchingService;
    private final SpoolAllocationService spoolAllocationService;

    public FilamentCostPreviewDto preview(
            PrinterState printerState,
            org.spon.edol.model.Filament filamentDto
    ) {

        Filament filament =
                filamentMatchingService.match(
                        printerState,
                        filamentDto
                );

        List<AllocationResult> allocations =
                spoolAllocationService.previewAllocation(
                        filament,
                        filamentDto.getUsedGrams()
                );

        FilamentCostPreviewDto dto = new FilamentCostPreviewDto();

        dto.setAllocations(
                allocations.stream()
                        .map(a -> {
                            AllocationResultDto item = new AllocationResultDto();
                            item.setSpoolId(a.getSpool().getId());
                            item.setAllocatedGrams(a.getAllocatedGrams());
                            item.setCost(a.getCost().doubleValue());
                            return item;
                        })
                        .toList()
        );

        dto.setTotalCost(
                allocations.stream()
                        .map(AllocationResult::getCost)
                        .mapToDouble(BigDecimal::doubleValue)
                        .sum()
        );

        return dto;

    }

}
