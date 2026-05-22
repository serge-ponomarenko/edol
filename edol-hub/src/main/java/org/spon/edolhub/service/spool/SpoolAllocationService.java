package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.service.JobSpoolUsageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpoolAllocationService {

    private final SpoolResolverService spoolResolverService;
    private final JobSpoolUsageService jobSpoolUsageService;

    public List<AllocationResult> previewAllocation(
            Filament filament,
            double estimatedGrams
    ) {
        return calculateAllocations(
                filament,
                estimatedGrams
        );
    }

    public List<JobSpoolUsage> allocate(
            PrintJob job,
            Filament filament,
            double estimatedGrams
    ) {
        List<AllocationResult> allocations =
                calculateAllocations(
                        filament,
                        estimatedGrams
                );

        List<JobSpoolUsage> usages = new ArrayList<>();

        for (AllocationResult allocation : allocations) {
            JobSpoolUsage usage =
                    jobSpoolUsageService.create(
                            job,
                            allocation.getSpool()
                    );

            usage.setUsedGrams(
                    allocation.getAllocatedGrams().doubleValue()
            );

            usage.setCost(allocation.getCost());

            usages.add(usage);
        }
        return usages;
    }

    private List<AllocationResult> calculateAllocations(
            Filament filament,
            double estimatedGrams
    ) {
        int remainingToAllocate = (int) Math.ceil(estimatedGrams);

        List<AllocationResult> allocations = new ArrayList<>();

        List<FilamentSpool> candidates =
                spoolResolverService.resolveCandidates(
                        filament
                );

        for (FilamentSpool spool : candidates) {
            if (remainingToAllocate <= 0) {
                break;
            }

            int spoolRemaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            if (spoolRemaining <= 0) {
                continue;
            }

            int allocated =
                    Math.min(
                            spoolRemaining,
                            remainingToAllocate
                    );

            BigDecimal cost =
                    calculateCost(
                            spool,
                            allocated
                    );

            allocations.add(
                    new AllocationResult(
                            spool,
                            allocated,
                            cost
                    )
            );

            log.info("""
                            [Spool Allocation Preview]
                            Spool ID: {},
                            Color: {},
                            Usage: {}g
                            """,
                    spool.getId(),
                    spool.getFilament().getColorHex(),
                    allocated
            );

            remainingToAllocate -= allocated;

        }

        if (remainingToAllocate > 0) {
            log.warn("""
                            Not enough filament for allocation!
                            Filament: {},
                            Missing: {}g
                            """,
                    filament.getFullId(),
                    remainingToAllocate
            );
        }

        return allocations;

    }

    private static @NonNull BigDecimal calculateCost(FilamentSpool spool, int allocated) {
        BigDecimal cost = BigDecimal.ZERO;

        if (
                spool.getPrice() != null
                        && spool.getWeightTotal() != null
                        && spool.getWeightTotal() > 0
        ) {

            BigDecimal costPerGram =
                    spool.getPrice().divide(
                            BigDecimal.valueOf(
                                    spool.getWeightTotal()
                            ),
                            4,
                            RoundingMode.HALF_UP
                    );

            cost = costPerGram.multiply(
                    BigDecimal.valueOf(allocated)
            );

        }
        return cost;
    }

}