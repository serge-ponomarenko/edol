package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.JobSpoolUsageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintAllocationFinalizeService {

    private final PrintAllocationPreviewRepository previewRepository;
    private final JobSpoolUsageService jobSpoolUsageService;
    private final SpoolConsumptionService spoolConsumptionService;

    public boolean finalizeAllocation(
            PrintJob job
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(job.getId())
                        .orElse(null);

        if (preview == null) {
            log.warn("No allocation preview found for print job {}",
                    job.getId()
            );
            return false;
        }

        boolean unresolved =
                preview.getGroups()
                        .stream()
                        .anyMatch(group ->
                                group.getStatus()
                                        != AllocationStatus.RESOLVED
                        );

        if (unresolved) {
            log.warn("Allocation preview for print job {} is not fully resolved. Finalization skipped.",
                    job.getId()
            );
            return false;
        }

        List<AllocationResult> allocations = new ArrayList<>();

        for (PrintAllocationGroup group : preview.getGroups()) {
            for (PrintAllocationItem item : group.getItems()) {
                allocations.add(
                        new AllocationResult(
                                item.getSpool(),
                                item.getAllocatedGrams(),
                                item.getEstimatedCost()
                        )
                );
            }
        }

        finalizeUsages(job, allocations);

        preview.setFinalized(true);

        previewRepository.save(preview);

        return true;
    }

    private void finalizeUsages(
            PrintJob job,
            List<AllocationResult> allocations
    ) {
        List<JobSpoolUsage> usages = new ArrayList<>();

        for (AllocationResult allocation : allocations) {
            JobSpoolUsage usage =
                    jobSpoolUsageService.create(
                            job,
                            allocation.getSpool()
                    );

            usage.setUsedGrams(allocation.getAllocatedGrams().doubleValue());
            usage.setCost(allocation.getCost());
            usages.add(usage);

        }

        spoolConsumptionService.consume(usages);

    }

}