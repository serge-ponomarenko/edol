package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.spool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintJobService {

    private final RestTemplate restTemplate;

    private final PrintJobRepository printJobRepository;
    private final PrinterStatsService printerStatsService;

    private final PrintRuntimeStateService runtimeStateService;

    private final PrintAllocationSnapshotService printAllocationSnapshotService;
    private final PrintAllocationFinalizeService printAllocationFinalizeService;
    private final PrintAllocationPreviewService printAllocationPreviewService;
    private final SpoolAllocationService spoolAllocationService;
    private final PrintAllocationPreviewRepository previewRepository;
    private final AllocationPreviewRuntimeCacheService runtimeCacheService;
    private final AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;


    @Value("${edol-core.url}")
    private String edolCoreUrl;


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

        PrintJob savedJob = printJobRepository.save(job);

        runtimeStateService.setCurrentJob(savedJob);
        runtimeStateService.setAllocationPreviewReady(false);

        log.info(
                "Print started. Session ID: {}, Job ID: {}",
                savedJob.getSessionId(),
                savedJob.getId()
        );

    }

    @Transactional
    public void metadataLoaded(
            PrinterState printerState
    ) {
        PrintJob job = getCurrentJob();

        if (previewRepository.existsByPrintJobId(
                job.getId()
        )) {
            return;
        }

        printAllocationSnapshotService.createSnapshot(
                job,
                printerState
        );

        runtimeStateService
                .setAllocationPreviewReady(true);

        allocationPreviewRuntimeSyncService.refresh(job.getId());

        log.info(
                "Allocation snapshot created. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId()
        );

        saveModelImage(job);
    }

    @Transactional
    public void finish(PrinterState printerState) {
        PrintJob job = getCurrentJob(printerState);

        job.setStatus(PrintJobStatus.FINISHED);
        printAllocationFinalizeService.finalizeAllocation(job);

        job.setCurrentLayer(printerState.getTotalLayers());
        job.setProgress(100);
        job.setFinishedAt(LocalDateTime.now());

        updatePrinterStats(job);

        runtimeStateService.setAllocationPreviewReady(false);

        runtimeCacheService.setCurrentAllocationPreview(null);

        runtimeStateService.setCurrentJob(null);

        log.info("Print finished. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId());

        printJobRepository.save(job);
    }

    @Transactional
    public void cancel(PrinterState printerState) {
        PrintJob job = getCurrentJob(printerState);

        if (isTerminalStatus(job))
            return; // may already be set by PRINT_ERROR or PRINT_FAILED events

        int progress = printerState.getProgress();
        int totalLayers = printerState.getTotalLayers();
        int currentLayer = printerState.getLayer();

        for (org.spon.edol.model.Filament filamentDto : printerState.getFilaments()) {
            double usedGrams =
                    estimateInterruptedUsage(
                            filamentDto.getUsedGrams(),
                            currentLayer,
                            totalLayers,
                            progress
                    );

            PrintAllocationGroup group =
                    resolveAllocationGroup(
                            job,
                            filamentDto
                    );

            if (group == null) {
                continue;
            }

            Filament filament = group.getFilament();

            List<AllocationResult> allocations =
                    spoolAllocationService
                            .previewAllocation(
                                    filament,
                                    usedGrams
                            );

            printAllocationPreviewService
                    .updateActualUsage(
                            job,
                            filament,
                            usedGrams,
                            allocations
                    );
        }

        printAllocationFinalizeService.finalizeAllocation(job);

        job.setProgress(progress);

        if (printerState.getError() != null && printerState.getError().getCode() == 50348044) {
            job.setStatus(PrintJobStatus.CANCELLED);
        } else {
            job.setStatus(PrintJobStatus.FAILED);
        }
        job.setFinishedAt(LocalDateTime.now());

        runtimeStateService.setAllocationPreviewReady(false);

        runtimeCacheService.setCurrentAllocationPreview(null);

        runtimeStateService.setCurrentJob(null);

        updatePrinterStats(job);

        log.info("Print canceled or failed. Session ID: {}, Job ID: {}",
                printerState.getSessionId(),
                job.getId());

        printJobRepository.save(job);
    }

    @Transactional
    public void updateProgress(PrinterState printerState) {
        PrintJob job = getCurrentJob(printerState);

        if (job.getStatus() != PrintJobStatus.FINISHED
                && job.getStatus() != PrintJobStatus.FAILED
                && job.getStatus() != PrintJobStatus.CANCELLED
        ) {
            job.setProgress(printerState.getProgress());
            job.setCurrentLayer(printerState.getLayer());
            job.setTotalLayers(printerState.getTotalLayers());
            job.setRemainingTime(printerState.getRemainingTime());

            log.info("Progress updated: {}%, layer: {}/{}. Session ID: {}, Job ID: {}",
                    printerState.getProgress(),
                    printerState.getLayer(),
                    printerState.getTotalLayers(),
                    printerState.getSessionId(),
                    job.getId());

            printJobRepository.save(job);
        }
    }

    public void saveModelImage(PrintJob job) {
        CompletableFuture.runAsync(() -> fetchAndSave(job));
    }

    private void fetchAndSave(PrintJob job) {
        String url = edolCoreUrl + "/api/modelimage";

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );

        job.setPlateImage(response.getBody());
        job.setPlateImageType("image/png");

        log.info("Model image saved. Job ID: {}", job.getId());

        printJobRepository.save(job);
    }

    private PrintJob getCurrentJob() {
        PrintJob job = runtimeStateService.getCurrentJob();

        if (job == null) {
            throw new IllegalStateException("No active print job");
        }

        return job;
    }

    private PrintJob getCurrentJob(PrinterState printerState) {
        PrintJob job = runtimeStateService.getCurrentJob();

        if (job == null) {
            throw new IllegalStateException(
                    "No active print job for printer " + printerState.getPrinterId()
            );
        }

        if (!job.getSessionId().equals(printerState.getSessionId())) {
            throw new IllegalStateException(
                    "Active print job session mismatch. " +
                            "Expected " + job.getSessionId() +
                            ", received " + printerState.getSessionId()
            );
        }

        return job;
    }

    private PrintAllocationGroup resolveAllocationGroup(
            PrintJob job,
            org.spon.edol.model.Filament filamentDto
    ) {
        PrintAllocationPreview preview =
                previewRepository
                        .findByPrintJobId(
                                job.getId()
                        )
                        .orElse(null);

        if (preview == null) {
            log.warn(
                    "No allocation preview found for job {}. Skipping interrupted usage for AMS slot {}.",
                    job.getId(),
                    filamentDto.getAmsSlot()
            );
            return null;
        }

        return preview.getGroups()
                .stream()
                .filter(g ->
                        g.getAmsSlot() != null
                                && filamentDto.getAmsSlot() != null
                                && g.getAmsSlot()
                                .equals(
                                        filamentDto.getAmsSlot()
                                )
                )
                .findFirst()
                .orElse(null);
    }


    private void updatePrinterStats(
            PrintJob job
    ) {
        long totalFilamentUsage =
                (long) job.getJobSpoolUsages()
                        .stream()
                        .mapToDouble(
                                JobSpoolUsage::getUsedGrams
                        )
                        .sum();

        printerStatsService.addPrintJob(
                job.getPrintDuration()
                        .toSeconds(),
                totalFilamentUsage
        );

    }

    private boolean isTerminalStatus(
            PrintJob job
    ) {
        return job.getStatus() == PrintJobStatus.FINISHED
                || job.getStatus() == PrintJobStatus.FAILED
                || job.getStatus() == PrintJobStatus.CANCELLED;
    }

    private double estimateInterruptedUsage(
            double plannedGrams,
            int currentLayer,
            int totalLayers,
            int progress
    ) {
        double ratio =
                totalLayers > 0
                        ? (double) currentLayer / totalLayers
                        : progress / 100.0;

        ratio = Math.clamp(
                ratio
                ,
                0,
                1);

        return Math.round(
                plannedGrams * ratio * 100.0
        ) / 100.0;
    }

}
