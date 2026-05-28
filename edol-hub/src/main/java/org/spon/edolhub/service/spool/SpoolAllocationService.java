package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.spon.edolhub.config.GramUtils.GRAM_EPSILON;
import static org.spon.edolhub.config.GramUtils.round;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpoolAllocationService {

    private final SpoolResolverService spoolResolverService;


    public List<AllocationResult> previewAllocation(
            Filament filament,
            double estimatedGrams
    ) {
        return calculateAllocations(
                filament,
                estimatedGrams
        );
    }


    private List<AllocationResult> calculateAllocations(
            Filament filament,
            double estimatedGrams
    ) {
        double remainingToAllocate =
                round(estimatedGrams);

        List<AllocationResult> allocations = new ArrayList<>();

        List<FilamentSpool> candidates =
                spoolResolverService.resolveCandidates(
                        filament
                );

        for (FilamentSpool spool : candidates) {
            if (remainingToAllocate <= GRAM_EPSILON) {
                break;
            }

            double spoolRemaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : spool.getWeightTotal();

            if (spoolRemaining <= GRAM_EPSILON) {
                continue;
            }

            double allocated =
                    round(
                            Math.min(
                                    spoolRemaining,
                                    remainingToAllocate
                            )
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

            remainingToAllocate =
                    Math.max(
                            0.0,
                            round(
                                    remainingToAllocate - allocated
                            )
                    );

        }

        if (remainingToAllocate > GRAM_EPSILON) {
            log.warn("""
                            Not enough filament for allocation!
                            Filament: #{} {},
                            Missing: {}g
                            """,
                    filament.getId(),
                    filament.getFullId(),
                    remainingToAllocate
            );
        }

        return allocations;

    }

    private static @NonNull BigDecimal calculateCost(FilamentSpool spool, double allocated) {
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