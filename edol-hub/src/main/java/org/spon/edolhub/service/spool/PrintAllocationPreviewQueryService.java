package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationResultDto;
import org.spon.edolhub.model.dto.FilamentCostPreviewDto;
import org.spon.edolhub.model.entity.PrintAllocationGroup;
import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PrintAllocationPreviewQueryService {

    private final PrintAllocationPreviewRepository previewRepository;

    public FilamentCostPreviewDto getPreview(
            Long printJobId,
            Long filamentId
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(printJobId)
                        .orElseThrow();

        PrintAllocationGroup group =
                preview.getGroups()
                        .stream()
                        .filter(g ->
                                g.getFilament() != null
                                        && g.getFilament().getId()
                                        .equals(filamentId)
                        )
                        .findFirst()
                        .orElse(null);

        if (group == null) {
            return null;
        }

        FilamentCostPreviewDto dto = new FilamentCostPreviewDto();

        dto.setAllocations(
                group.getItems()
                        .stream()
                        .map(this::mapItem)
                        .toList()
        );

        dto.setTotalCost(
                group.getItems()
                        .stream()
                        .map(PrintAllocationItem::getEstimatedCost)
                        .filter(Objects::nonNull)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .doubleValue()
        );

        return dto;

    }

    private AllocationResultDto mapItem(
            PrintAllocationItem item
    ) {
        AllocationResultDto dto = new AllocationResultDto();
        dto.setSpoolId(item.getSpool().getId());
        dto.setAllocatedGrams(item.getAllocatedGrams());
        dto.setCost(item.getEstimatedCost() != null ? item.getEstimatedCost().doubleValue() : 0);
        return dto;
    }

}