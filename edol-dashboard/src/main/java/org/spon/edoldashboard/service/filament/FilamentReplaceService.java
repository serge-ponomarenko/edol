package org.spon.edoldashboard.service.filament;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edoldashboard.model.dto.FilamentReplacePreviewDto;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.model.entity.JobFilamentUsage;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.JobFilamentUsageRepository;
import org.spon.edoldashboard.service.PrintJobService;
import org.spon.edoldashboard.service.spool.FilamentSpoolService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilamentReplaceService {

    private final FilamentRepository filamentRepository;
    private final FilamentSpoolRepository spoolRepository;
    private final FilamentSpoolService spoolService;
    private final PrintJobService printJobService;
    private final JobFilamentUsageRepository jobFilamentUsageRepository;

    @Transactional
    public void replace(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot replace filament with itself");
        }

        Filament source = filamentRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source filament not found"));

        Filament target = filamentRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target filament not found"));

        List<Long> filamentUsagesJobIdList = jobFilamentUsageRepository
                .findByFilamentId(source.getId()).stream()
                .map(fu -> fu.getPrintJob().getId())
                .toList();

        // 🔥 1. Move usages (bulk)
        int updated = jobFilamentUsageRepository.moveUsages(sourceId, targetId);

        // 🔹 2. Delete source spools
        spoolRepository.deleteAllByFilamentId(sourceId);

        // 🔹 3. Recalculate target spools
        spoolService.recalculateSpoolsForJobs(target, filamentUsagesJobIdList);

        filamentUsagesJobIdList.forEach(printJobService::recalculateCost);

        // 🔹 4. Delete source filament
        filamentRepository.delete(source);

        // optional log
        log.info("Replaced filament {} -> {} ({} usages moved)",
                sourceId, targetId, updated);
    }

    public FilamentReplacePreviewDto preview(Long filamentId) {

        List<JobFilamentUsage> usages =
                jobFilamentUsageRepository.findByFilamentId(filamentId);

        double totalGrams = usages.stream()
                .mapToDouble(JobFilamentUsage::getUsedGrams)
                .sum();

        Map<Long, List<JobFilamentUsage>> byJob =
                usages.stream().collect(Collectors.groupingBy(u -> u.getPrintJob().getId()));

        List<FilamentReplacePreviewDto.JobUsageDto> jobs = byJob.entrySet()
                .stream()
                .map(entry -> {

                    Long jobId = entry.getKey();
                    List<JobFilamentUsage> jobUsages = entry.getValue();

                    double jobGrams = jobUsages.stream()
                            .mapToDouble(JobFilamentUsage::getUsedGrams)
                            .sum();

                    String jobName = jobUsages.get(0).getPrintJob().getTaskName();

                    return new FilamentReplacePreviewDto.JobUsageDto(
                            jobId,
                            jobName,
                            jobGrams
                    );
                })
                .sorted((o1, o2) -> Math.toIntExact(o2.getJobId() - o1.getJobId()))
                .toList();

        return FilamentReplacePreviewDto.builder()
                .usagesCount((long) usages.size())
                .totalGrams(totalGrams)
                .jobsCount((long) jobs.size())
                .jobs(jobs)
                .build();
    }
}
