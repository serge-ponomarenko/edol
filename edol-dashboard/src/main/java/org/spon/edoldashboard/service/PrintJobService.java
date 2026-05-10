package org.spon.edoldashboard.service;

import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.ExtTray;
import org.spon.edol.model.PrinterState;
import org.spon.edoldashboard.model.entity.*;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.JobFilamentUsageRepository;
import org.spon.edoldashboard.repository.PrintJobRepository;
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
    private final JobFilamentUsageRepository jobFilamentUsageRepository;
    private final RestTemplate restTemplate;

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
            org.spon.edol.model.AmsSlot amsSlot = printerState.isExternalSpoolUsed() ? null : printerState.getAms().getSlots().get(filamentDto.getAmsSlot());
            ExtTray extTray = printerState.isExternalSpoolUsed() ? printerState.getExtTray() : null;

            calculateJobFilamentUsage(filamentDto, usedGrams, job, amsSlot, extTray);

        }

        job.setStatus(PrintJobStatus.FINISHED);
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

            double usedGrams = filamentDto.getUsedGrams() * ((double) currentLayer / totalLayers);
            org.spon.edol.model.AmsSlot amsSlot = printerState.isExternalSpoolUsed() ? null : printerState.getAms().getSlots().get(filamentDto.getAmsSlot());
            ExtTray extTray = printerState.isExternalSpoolUsed() ? printerState.getExtTray() : null;
            calculateJobFilamentUsage(filamentDto, usedGrams, job, amsSlot, extTray);
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
                                           double usedGrams, PrintJob job, org.spon.edol.model.AmsSlot amsSlot, ExtTray extTray) {
        String filamentColor = filamentDto.getColor();

        if (amsSlot != null && !amsSlot.getColor().substring(0, 6).equals(filamentDto.getColor().substring(1))) {
            log.warn("Filament {}, #{} -> AMS: #{} has color mismatch with AMS mapping! AMS color will be used.",
                    filamentDto.getFullId(),
                    filamentDto.getColor().substring(1),
                    amsSlot.getColor().substring(0, 6)
            );
            filamentColor = "#" + amsSlot.getColor().substring(0, 6);

        }
        if (extTray != null) {
            if (!extTray.getColor().substring(0, 6).equals(filamentDto.getColor().substring(1))) {
                log.warn("Filament {}, #{} -> EXT: #{} has color mismatch with EXT Tray mapping! EXT Tray color will be used.",
                        filamentDto.getFullId(),
                        filamentDto.getColor().substring(1),
                        extTray.getColor().substring(0, 6)
                );
                filamentColor = "#" + extTray.getColor().substring(0, 6);
            }
        }

        // 1. Find or create Filament
        Filament filament = filamentService.findOrCreateFilament(
                filamentDto.getFullId(),
                filamentColor,
                filamentDto.getFilamentBrandIndex()
        );

        int remainingToConsume = (int) usedGrams;

        FilamentSpool initialSpool = null;

        while (remainingToConsume > 0) {
            // Find current active spool or create new
            FilamentSpool spool =
                    filamentSpoolService.findOrCreateForFilament(filament);
            if (initialSpool == null) {
                initialSpool = spool;
            }

            int spoolRemaining =
                    spool.getWeightRemaining() != null
                            ? spool.getWeightRemaining()
                            : 1000;

            // How much we can consume from this spool
            int consumed = Math.min(spoolRemaining, remainingToConsume);

            int newRemaining = spoolRemaining - consumed;

            spool.setWeightRemaining(newRemaining);
            spool.setLastUsedAt(LocalDateTime.now());

            if (newRemaining == 0) {
                spool.setStatus(FilamentSpool.FilamentSpoolStatus.EMPTY);
            } else {
                spool.setStatus(FilamentSpool.FilamentSpoolStatus.ACTIVE);
            }

            filamentSpoolRepository.save(spool);

            remainingToConsume -= consumed;
        }

        // 4. Calculate cost
        BigDecimal cost = BigDecimal.ZERO;

        if (initialSpool != null &&
                initialSpool.getPrice() != null &&
                initialSpool.getWeightTotal() != null &&
                initialSpool.getWeightTotal() > 0) {

            BigDecimal costPerGram =
                    initialSpool.getPrice().divide(
                            BigDecimal.valueOf(initialSpool.getWeightTotal()),
                            4,
                            RoundingMode.HALF_UP
                    );

            cost = costPerGram.multiply(BigDecimal.valueOf(usedGrams));
        }

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
