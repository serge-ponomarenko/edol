package org.spon.edoldashboard.service;

import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edoldashboard.model.entity.*;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.JobFilamentUsageRepository;
import org.spon.edoldashboard.repository.PrintJobRepository;
import org.spon.edoldashboard.service.filament.FilamentMatchingService;
import org.spon.edoldashboard.service.filament.FilamentService;
import org.spon.edoldashboard.service.spool.FilamentSpoolService;
import org.spon.edoldashboard.service.spool.SpoolAllocationService;
import org.spon.edoldashboard.service.spool.SpoolConsumptionService;
import org.spon.edoldashboard.service.spool.SpoolResolverService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintJobService {

    private final PrintJobRepository printJobRepository;
    private final PrinterStatsService printerStatsService;
    private final FilamentService filamentService;
    private final FilamentSpoolService filamentSpoolService;
    private final FilamentSpoolRepository filamentSpoolRepository;

    private final SpoolResolverService spoolResolverService;

    private final JobFilamentUsageRepository jobFilamentUsageRepository;
    private final JobSpoolUsageService jobSpoolUsageService;
    private final RestTemplate restTemplate;
    private final FilamentMatchingService filamentMatchingService;
    private final SpoolAllocationService spoolAllocationService;
    private final SpoolConsumptionService spoolConsumptionService;

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @Getter
    private PrintJob currentJob;

    public Page<PrintJob> getJobs(int page, int size) {
        return printJobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
    }

    @Transactional
    public void start(PrinterState printerState) {
        PrintJob job = PrintJob.builder()
                .printerId(printerState.getPrinterId())
                .sessionId(printerState.getSessionId())
                .fileName(printerState.getCurrentFile())
                .taskName(printerState.getCurrentTask())
                .status(PrintJobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();

        currentJob = printJobRepository.save(job);

        log.info("Print started. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                currentJob.getId());
    }

    @Transactional
    public void finish(PrinterState printerState) {
        PrintJob job = printJobRepository.findBySessionId(printerState.getSessionId())
                .orElseThrow();

        for (org.spon.edol.model.Filament filamentDto : printerState.getFilaments()) {
            double usedGrams = filamentDto.getUsedGrams();
            calculateJobFilamentUsage(filamentDto, usedGrams, job, printerState);
        }

        job.setStatus(PrintJobStatus.FINISHED);
        job.setCurrentLayer(printerState.getTotalLayers());
        job.setProgress(100);
        job.setFinishedAt(LocalDateTime.now());

        updatePrinterStats(job);

        currentJob = null;

        log.info("Print finished. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId());

        printJobRepository.save(job);
    }

    @Transactional
    public void cancel(PrinterState printerState) {
        PrintJob job = printJobRepository.findBySessionId(printerState.getSessionId())
                .orElseThrow();

        if (job.getStatus() == PrintJobStatus.FAILED)
            return; // may already be set by PRINT_ERROR or PRINT_FAILED events

        int progress = printerState.getProgress();
        int totalLayers = printerState.getTotalLayers();
        int currentLayer = printerState.getLayer();

        for (org.spon.edol.model.Filament filamentDto : printerState.getFilaments()) {
            double usedGrams = Math.round(filamentDto.getUsedGrams() * ((double) currentLayer / totalLayers) * 100.0) / 100.0;
            calculateJobFilamentUsage(filamentDto, usedGrams, job, printerState);
        }

        job.setProgress(progress);
        if (printerState.getError() != null && printerState.getError().getCode() == 50348044) {
            job.setStatus(PrintJobStatus.CANCELLED);
        } else {
            job.setStatus(PrintJobStatus.FAILED);
        }
        job.setFinishedAt(LocalDateTime.now());

        currentJob = null;

        updatePrinterStats(job);

        log.info("Print canceled or failed. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId());

        printJobRepository.save(job);
    }

    @Transactional
    public void updateProgress(PrinterState printerState) {
        PrintJob job = printJobRepository
                .findBySessionId(printerState.getSessionId())
                .orElseThrow();

        if (job.getStatus() != PrintJobStatus.FINISHED
                && job.getStatus() != PrintJobStatus.FAILED
                && job.getStatus() != PrintJobStatus.CANCELLED
        ) {
            job.setProgress(printerState.getProgress());
            job.setCurrentLayer(printerState.getLayer());
            job.setTotalLayers(printerState.getTotalLayers());
            job.setRemainingTime(printerState.getRemainingTime());

            if (currentJob == null) {
                currentJob = job;
            }

            log.info("Progress updated: {}%, layer: {}/{}. Session ID: {}, Job ID: {}",
                    printerState.getProgress(),
                    printerState.getLayer(),
                    printerState.getTotalLayers(),
                    printerState.getSessionId(),
                    job.getId());

            printJobRepository.save(job);
        }
    }

    public void saveModelImage(PrinterState printerState) {
        CompletableFuture.runAsync(() -> fetchAndSave(printerState));
    }

    public void fetchAndSave(PrinterState printerState) {
        PrintJob job = printJobRepository.findBySessionId(printerState.getSessionId()).orElseThrow();

        String url = edolCoreUrl + "/printer/modelimage";

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );

        job.setPlateImage(response.getBody());
        job.setPlateImageType("image/png");

        log.info("Model image saved. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId());

        printJobRepository.save(job);
    }

    private void calculateJobFilamentUsage(org.spon.edol.model.Filament filamentDto,
                                           double usedGrams, PrintJob job, PrinterState printerState) {
        Filament filament =
                filamentMatchingService.match(
                        printerState,
                        filamentDto
                );

        log.info("[Filament Usage] Job ID: {}, Filament Full ID: {}, Color: {}, Usage: {}g",
                job.getId(),
                filament.getFullId(),
                filament.getColorHex(),
                usedGrams
        );

        List<JobSpoolUsage> spoolUsages =
                spoolAllocationService.allocate(
                        job,
                        filament,
                        usedGrams
                );

        spoolConsumptionService.consume(
                spoolUsages
        );

        // 5. Aggregate filament cost from spool usage
        BigDecimal cost =
                spoolUsages.stream()
                        .map(JobSpoolUsage::getCost)
                        .filter(Objects::nonNull)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        // 5. Save usage
        JobFilamentUsage usage = JobFilamentUsage.builder()
                .printJob(job)
                .filament(filament)
                .usedGrams(usedGrams)
                .usedMeters(filamentDto.getUsedMeters())
                .usedForObject(filamentDto.isUsedForObject())
                .usedForSupport(filamentDto.isUsedForSupport())
                .cost(cost)
                .build();

        jobFilamentUsageRepository.save(usage);
    }

    @Transactional
    public void recalculateCost(Long jobId) {
        PrintJob job = printJobRepository.findById(jobId)
                .orElseThrow();

        job.getJobFilamentUsages().forEach(filamentUsage -> {
            FilamentSpool spool = filamentSpoolRepository
                    .findFirstByFilamentIdAndStatus(filamentUsage.getFilament().getId(), FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .orElseThrow();
            Double usedGrams = filamentUsage.getUsedGrams();
            BigDecimal pricePerGram = spool.getPrice().divide(
                    BigDecimal.valueOf(spool.getWeightTotal()),
                    4,
                    RoundingMode.HALF_UP
            );
            filamentUsage.setCost(pricePerGram.multiply(BigDecimal.valueOf(usedGrams)));
        });

        printJobRepository.save(job);
    }


    private void updatePrinterStats(PrintJob job) {
        long totalFilamentUsage = (long) job.getJobFilamentUsages()
                .stream()
                .mapToDouble(JobFilamentUsage::getUsedGrams)
                .sum();

        printerStatsService.addPrintJob(
                job.getPrintDuration().toSeconds(),
                totalFilamentUsage
        );
    }

}
