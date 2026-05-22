package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.JobSpoolUsageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobSpoolUsageService {

    private final JobSpoolUsageRepository jobSpoolUsageRepository;

    public JobSpoolUsage create(
            PrintJob printJob,
            FilamentSpool filamentSpool
    ) {
        JobSpoolUsage usage = JobSpoolUsage.builder()
                .printJob(printJob)
                .filamentSpool(filamentSpool)
                .usedGrams(0.0)
                .createdAt(LocalDateTime.now())
                .build();

        return jobSpoolUsageRepository.save(usage);
    }

    public JobSpoolUsage updateUsedGrams(
            Long id,
            Double usedGrams
    ) {
        JobSpoolUsage usage = jobSpoolUsageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("JobSpoolUsage not found"));
        usage.setUsedGrams(usedGrams);
        return jobSpoolUsageRepository.save(usage);
    }

    public List<JobSpoolUsage> findByPrintJob(Long printJobId) {
        return jobSpoolUsageRepository.findByPrintJobId(printJobId);
    }

    public List<JobSpoolUsage> findBySpool(Long spoolId) {
        return jobSpoolUsageRepository.findByFilamentSpoolId(spoolId);
    }

}
