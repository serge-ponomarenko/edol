package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.spon.edolhub.config.GramUtils.GRAM_EPSILON;
import static org.spon.edolhub.config.GramUtils.round;

@Service
@RequiredArgsConstructor
public class AllocationMutationService {

    private final PrintAllocationPreviewRepository
            previewRepository;
    private final AllocationResultMapper
            allocationResultMapper;
    private final SpoolAllocationService
            spoolAllocationService;
    private final PrintAllocationReconciliationService
            reconciliationService;
    private final AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @Transactional
    public void replaceAllocationWithSingleSpool(
            Long printJobId,
            Long filamentId,
            FilamentSpool spool,
            Double allocatedGrams,
            BigDecimal estimatedCost
    ) {
        PrintAllocationPreview preview =
                loadMutablePreview(printJobId);

        PrintAllocationGroup group =
                findGroupByFilamentId(
                        preview,
                        filamentId
                );

        validateReplaceAllocation(
                group,
                allocatedGrams
        );

        group.setUserOverridden(true);

        group.getItems().clear();

        addUserSelectedItem(
                group,
                spool,
                allocatedGrams,
                estimatedCost
        );

        recalculateGroupState(group);

        previewRepository.save(preview);

        allocationPreviewRuntimeSyncService.refresh(
                printJobId
        );

    }

    @Transactional
    public void addAllocationItem(
            Long printJobId,
            Long filamentId,
            FilamentSpool spool,
            Double allocatedGrams,
            BigDecimal estimatedCost
    ) {
        PrintAllocationPreview preview =
                loadMutablePreview(printJobId);

        PrintAllocationGroup group =
                findGroupByFilamentId(
                        preview,
                        filamentId
                );

        if (hasSpool(
                group,
                spool
        )) {
            throw new IllegalArgumentException(
                    "Spool is already allocated in this group"
            );
        }

        validateAddedAllocation(
                group,
                allocatedGrams
        );

        group.setUserOverridden(true);

        addUserSelectedItem(
                group,
                spool,
                allocatedGrams,
                estimatedCost
        );

        recalculateGroupState(group);

        previewRepository.save(preview);

        allocationPreviewRuntimeSyncService.refresh(
                printJobId
        );

    }

    @Transactional
    public void rerunAllocation(
            Long printJobId,
            Long filamentId
    ) {
        PrintAllocationPreview preview =
                loadMutablePreview(printJobId);

        PrintAllocationGroup group =
                findGroupByFilamentId(
                        preview,
                        filamentId
                );

        List<AllocationResult> allocations =
                spoolAllocationService
                        .previewAllocation(
                                group.getFilament(),
                                group.getRequestedGrams()
                        );

        replaceGroupItems(
                group,
                allocations
        );

        group.setUserOverridden(false);

        recalculateGroupState(group);

        previewRepository.save(preview);

        allocationPreviewRuntimeSyncService.refresh(
                printJobId
        );
    }

    @Transactional
    public void replaceFilament(
            Long printJobId,
            Long sourceFilamentId,
            Filament targetFilament
    ) {
        PrintAllocationPreview preview =
                loadMutablePreview(printJobId);

        PrintAllocationGroup group =
                findGroupByFilamentId(
                        preview,
                        sourceFilamentId
                );

        group.setFilament(targetFilament);

        List<AllocationResult> allocations =
                spoolAllocationService
                        .previewAllocation(
                                targetFilament,
                                group.getRequestedGrams()
                        );

        replaceGroupItems(
                group,
                allocations
        );

        group.setUserOverridden(true);

        recalculateGroupState(group);

        previewRepository.save(preview);

        allocationPreviewRuntimeSyncService.refresh(
                printJobId
        );

    }

    private PrintAllocationPreview loadMutablePreview(
            Long printJobId
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(printJobId)
                        .orElseThrow();

        if (!Boolean.TRUE.equals(
                preview.getFinalized()
        )) {
            return preview;
        }

        reconciliationService
                .rollbackFinalizedAllocation(
                        printJobId
                );

        return previewRepository
                .findByPrintJobId(printJobId)
                .orElseThrow();
    }

    private PrintAllocationGroup findGroupByFilamentId(
            PrintAllocationPreview preview,
            Long filamentId
    ) {
        return preview.getGroups()
                .stream()
                .filter(g ->
                        g.getFilament() != null
                                && g.getFilament()
                                .getId()
                                .equals(filamentId)
                )
                .findFirst()
                .orElseThrow();
    }

    private void addUserSelectedItem(
            PrintAllocationGroup group,
            FilamentSpool spool,
            Double allocatedGrams,
            BigDecimal estimatedCost
    ) {
        PrintAllocationItem item =
                allocationResultMapper.toItem(
                        group,
                        new AllocationResult(
                                spool,
                                allocatedGrams,
                                estimatedCost
                        )
                );

        item.setUserSelected(true);
        group.getItems().add(item);
    }

    private boolean hasSpool(
            PrintAllocationGroup group,
            FilamentSpool spool
    ) {
        return group.getItems()
                .stream()
                .map(PrintAllocationItem::getSpool)
                .filter(Objects::nonNull)
                .anyMatch(existing ->
                        existing.getId()
                                .equals(spool.getId())
                );
    }

    private void validateReplaceAllocation(
            PrintAllocationGroup group,
            Double allocatedGrams
    ) {
        validatePositiveGrams(allocatedGrams);

        if (
                allocatedGrams - group.getRequestedGrams() > GRAM_EPSILON
        ) {
            throw new IllegalArgumentException(
                    "Allocation exceeds requested job grams"
            );
        }
    }

    private void validateAddedAllocation(
            PrintAllocationGroup group,
            Double allocatedGrams
    ) {
        validatePositiveGrams(allocatedGrams);

        double remaining =
                group.getRequestedGrams()
                        - currentAllocatedGrams(group);

        if (allocatedGrams - remaining > GRAM_EPSILON) {
            throw new IllegalArgumentException(
                    "Allocation exceeds remaining job grams"
            );
        }
    }

    private void validatePositiveGrams(
            Double allocatedGrams
    ) {
        if (
                allocatedGrams == null
                        || allocatedGrams <= 0.0
        ) {
            throw new IllegalArgumentException(
                    "Allocation grams must be greater than zero"
            );
        }
    }

    private double currentAllocatedGrams(
            PrintAllocationGroup group
    ) {
        return round(
                group.getItems()
                        .stream()
                        .map(PrintAllocationItem::getAllocatedGrams)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .sum()
        );
    }

    private void replaceGroupItems(
            PrintAllocationGroup group,
            List<AllocationResult> allocations
    ) {
        group.getItems().clear();

        for (AllocationResult allocation : allocations) {
            group.getItems()
                    .add(
                            allocationResultMapper.toItem(
                                    group,
                                    allocation
                            )
                    );
        }
    }

    private void recalculateGroupState(
            PrintAllocationGroup group
    ) {
        double allocated =
                round(
                        currentAllocatedGrams(group)
                );

        group.setAllocatedGrams(allocated);

        double missing =
                round(
                        Math.max(
                                0,
                                group.getRequestedGrams()
                                        - allocated
                        )
                );

        group.setMissingGrams(missing);

        if (missing > GRAM_EPSILON) {
            group.setStatus(AllocationStatus.PARTIAL);
        } else {
            group.setStatus(AllocationStatus.RESOLVED);
        }

    }

}
