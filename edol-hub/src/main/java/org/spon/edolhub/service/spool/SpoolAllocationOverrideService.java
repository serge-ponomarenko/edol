package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SpoolAllocationOverrideService {

    private final PrintAllocationPreviewRepository
            previewRepository;
    private final AllocationResultMapper
            allocationResultMapper;
    private final SpoolAllocationService
            spoolAllocationService;
    private final PrintAllocationReconciliationService
            reconciliationService;
    private final PrintJobRepository
            printJobRepository;

    @Transactional
    public void overrideSingleSpool(
            Long printJobId,
            Long filamentId,
            FilamentSpool spool,
            Integer allocatedGrams,
            BigDecimal estimatedCost
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
                        .orElseThrow();

        group.setUserOverridden(true);

        group.getItems().clear();

        AllocationResult allocation =
                new AllocationResult(
                        spool,
                        allocatedGrams,
                        estimatedCost
                );

        PrintAllocationItem item =
                allocationResultMapper
                        .toItem(
                                group,
                                allocation
                        );

        item.setUserSelected(true);

        group.getItems().add(item);

        recalculateGroupState(group);

        previewRepository.save(preview);

    }

    @Transactional
    public void addAllocationItem(
            Long printJobId,
            Long filamentId,
            FilamentSpool spool,
            Integer allocatedGrams,
            BigDecimal estimatedCost
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
                                        && g.getFilament()
                                        .getId()
                                        .equals(filamentId)
                        )
                        .findFirst()
                        .orElseThrow();

        group.setUserOverridden(true);

        AllocationResult allocation =
                new AllocationResult(
                        spool,
                        allocatedGrams,
                        estimatedCost
                );

        PrintAllocationItem item =
                allocationResultMapper
                        .toItem(
                                group,
                                allocation
                        );

        item.setUserSelected(true);

        group.getItems().add(item);

        recalculateGroupState(group);

        previewRepository.save(preview);

    }

    @Transactional
    public void rerunAllocation(
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
                                        && g.getFilament()
                                        .getId()
                                        .equals(filamentId)
                        )
                        .findFirst()
                        .orElseThrow();

        List<AllocationResult> allocations =
                spoolAllocationService
                        .previewAllocation(
                                group.getFilament(),
                                group.getRequestedGrams()
                        );

        group.getItems().clear();

        for (AllocationResult allocation
                : allocations) {
            PrintAllocationItem item =
                    allocationResultMapper
                            .toItem(
                                    group,
                                    allocation
                            );
            group.getItems().add(item);
        }

        group.setUserOverridden(false);

        recalculateGroupState(group);

        previewRepository.save(preview);
    }

    @Transactional
    public void replaceFilament(
            Long printJobId,
            Long sourceFilamentId,
            Filament targetFilament
    ) {
        PrintJob job =
                printJobRepository
                        .findById(printJobId)
                        .orElseThrow();
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(printJobId)
                        .orElseThrow();

        if (Boolean.TRUE.equals(
                preview.getFinalized()
        )) {
            reconciliationService
                    .rollbackFinalizedAllocation(
                            printJobId
                    );

        }

        PrintAllocationGroup group =
                preview.getGroups()
                        .stream()
                        .filter(g ->
                                g.getFilament() != null
                                        && g.getFilament()
                                        .getId()
                                        .equals(sourceFilamentId)
                        )
                        .findFirst()
                        .orElseThrow();

        group.setFilament(targetFilament);

        List<AllocationResult> allocations =
                spoolAllocationService
                        .previewAllocation(
                                targetFilament,
                                group.getRequestedGrams()
                        );

        group.getItems().clear();

        for (AllocationResult allocation
                : allocations) {
            PrintAllocationItem item =
                    allocationResultMapper
                            .toItem(
                                    group,
                                    allocation
                            );
            group.getItems().add(item);
        }

        group.setUserOverridden(true);

        recalculateGroupState(group);

        previewRepository.save(preview);

    }

    private void recalculateGroupState(
            PrintAllocationGroup group
    ) {
        int allocated =
                group.getItems()
                        .stream()
                        .map(PrintAllocationItem::getAllocatedGrams)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum();

        group.setAllocatedGrams(allocated);

        int missing =
                Math.max(
                        0,
                        group.getRequestedGrams()
                                - allocated
                );

        group.setMissingGrams(missing);

        if (missing > 0) {
            group.setStatus(AllocationStatus.PARTIAL);
        } else {
            group.setStatus(AllocationStatus.RESOLVED);
        }

    }

}