package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.JobSpoolUsageRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.JobSpoolUsageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintAllocationReconciliationService {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintJobRepository printJobRepository;
    private final PrintAllocationFinalizeService finalizeService;
    private final JobSpoolUsageService jobSpoolUsageService;
    private final SpoolConsumptionService spoolConsumptionService;
    private final JobSpoolUsageRepository jobSpoolUsageRepository;

    @Transactional
    public boolean finalizeReconciliation(
            Long printJobId
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
            return true;
        }

        return finalizeService.finalizeAllocation(job);

    }

    @Transactional
    public void rollbackFinalizedAllocation(
            Long printJobId
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(printJobId)
                        .orElseThrow();

        if (!Boolean.TRUE.equals(
                preview.getFinalized()
        )) {
            return;
        }

        List<JobSpoolUsage> usages =
                jobSpoolUsageService
                        .findByPrintJob(
                                printJobId
                        );

        spoolConsumptionService.rollback(usages);

        jobSpoolUsageRepository.deleteAll(usages);

        preview.setFinalized(false);

        previewRepository.save(preview);

    }

}
