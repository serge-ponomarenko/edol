package org.spon.edolhub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterError;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.spool.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceTest {

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrinterStatsService printerStatsService;

    @Mock
    private PrintRuntimeStateService runtimeStateService;

    @Mock
    private PrintAllocationSnapshotService printAllocationSnapshotService;

    @Mock
    private PrintAllocationFinalizeService printAllocationFinalizeService;

    @Mock
    private PrintAllocationPreviewService printAllocationPreviewService;

    @Mock
    private SpoolAllocationService spoolAllocationService;

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private AllocationPreviewRuntimeCacheService runtimeCacheService;

    @Mock
    private AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @InjectMocks
    private PrintJobService printJobService;

    @Captor
    private ArgumentCaptor<PrintJob> jobCaptor;

    private PrinterState printerState;
    private PrintJob job;
    private org.spon.edol.model.Filament filamentDto;
    private org.spon.edolhub.model.entity.Filament hubFilament;
    private FilamentSpool spool;
    private Vendor vendor;
    private MaterialType materialType;

    private static final double PLANNED_GRAMS = 200.0;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(printJobService, "edolCoreUrl", "http://edolcore:8080");

        vendor = Vendor.builder().id(1L).name("JAMG HE").build();
        materialType = MaterialType.builder().id(1L).name("PLA").build();
        hubFilament = org.spon.edolhub.model.entity.Filament.builder()
                .id(1L).fullId("JAMG HE PLA Matte").vendor(vendor)
                .materialType(materialType).brand("Matte").colorHex("#F95D73")
                .build();
        spool = FilamentSpool.builder()
                .id(1L).filament(hubFilament).weightTotal(1000.0)
                .weightRemaining(500.0).price(BigDecimal.valueOf(25.00))
                .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                .build();

        printerState = new PrinterState();
        printerState.setPrinterId(1);
        printerState.setSessionId("SESS-001");
        printerState.setCurrentFile("test.gcode");
        printerState.setCurrentTask("Test Print");

        job = PrintJob.builder()
                .id(100L).sessionId("SESS-001").printerId(1)
                .fileName("test.gcode").taskName("Test Print")
                .status(PrintJobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .jobSpoolUsages(new ArrayList<>())
                .build();

        filamentDto = new org.spon.edol.model.Filament(5, "Pbec608f", "PLA", "#F95D73",
                "JAMG HE", "JAMG HE PLA Matte", 10.87, PLANNED_GRAMS, true, false, 0);
    }

    @Nested
    @DisplayName("start")
    class Start {

        @Test
        @DisplayName("creates print job and sets runtime state")
        void createsJobAndSetsRuntime() {
            when(printJobRepository.save(any())).thenReturn(job);
            when(runtimeStateService.getCurrentJob()).thenReturn(job);

            printJobService.start(printerState);

            verify(printJobRepository).save(jobCaptor.capture());
            PrintJob saved = jobCaptor.getValue();
            assertThat(saved.getPrinterId()).isEqualTo(1);
            assertThat(saved.getSessionId()).isEqualTo("SESS-001");
            assertThat(saved.getFileName()).isEqualTo("test.gcode");
            assertThat(saved.getTaskName()).isEqualTo("Test Print");
            assertThat(saved.getStatus()).isEqualTo(PrintJobStatus.RUNNING);
            assertThat(saved.getStartedAt()).isNotNull();

            verify(runtimeStateService).setCurrentJob(job);
            verify(runtimeStateService).setAllocationPreviewReady(false);
        }

    }

    @Nested
    @DisplayName("metadataLoaded")
    class MetadataLoaded {

        @Test
        @DisplayName("creates allocation snapshot and sets runtime ready")
        void createsSnapshot() {
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.existsByPrintJobId(job.getId())).thenReturn(false);

            printJobService.metadataLoaded(printerState);

            verify(printAllocationSnapshotService).createSnapshot(job, printerState);
            verify(runtimeStateService).setAllocationPreviewReady(true);
            verify(allocationPreviewRuntimeSyncService).refresh(job.getId());
        }

        @Test
        @DisplayName("skips snapshot when preview already exists")
        void skipsWhenPreviewExists() {
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.existsByPrintJobId(job.getId())).thenReturn(true);

            printJobService.metadataLoaded(printerState);

            verifyNoInteractions(printAllocationSnapshotService);
        }

        @Test
        @DisplayName("throws when job not found by session")
        void throwsWhenJobNotFound() {
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> printJobService.metadataLoaded(printerState))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(printAllocationSnapshotService);
        }

    }

    @Nested
    @DisplayName("finish")
    class Finish {

        @Test
        @DisplayName("finalizes allocation and marks job finished")
        void finalizesAndMarksFinished() {
            job.getJobSpoolUsages().add(JobSpoolUsage.builder().usedGrams(200.0).build());
            job.setStartedAt(LocalDateTime.now().minusMinutes(30));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printerState.setTotalLayers(160);
            printerState.setProgress(100);
            printJobService.finish(printerState);

            verify(printAllocationFinalizeService).finalizeAllocation(job);

            verify(printJobRepository).save(jobCaptor.capture());
            PrintJob saved = jobCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PrintJobStatus.FINISHED);
            assertThat(saved.getCurrentLayer()).isEqualTo(160);
            assertThat(saved.getProgress()).isEqualTo(100);
            assertThat(saved.getFinishedAt()).isNotNull();

            verify(runtimeStateService).setAllocationPreviewReady(false);
            verify(runtimeCacheService).setCurrentAllocationPreview(null);
            verify(runtimeStateService).setCurrentJob(null);
            verify(printerStatsService).addPrintJob(any(Long.class), eq(200L));
        }

    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        private PrintAllocationGroup group;
        private PrintAllocationPreview preview;

        @BeforeEach
        void setUp() {
            group = PrintAllocationGroup.builder()
                    .id(1L).filament(hubFilament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(PLANNED_GRAMS).allocatedGrams(PLANNED_GRAMS).missingGrams(0.0)
                    .userOverridden(false).amsSlot(0).build();
            preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false).groups(List.of(group)).build();
            group.setPreview(preview);
        }

        @Test
        @DisplayName("cancels with user cancelled error code")
        void cancelledByUser() {
            printerState.setProgress(50);
            printerState.setLayer(80);
            printerState.setTotalLayers(160);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, "User cancelled"));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(spoolAllocationService.previewAllocation(hubFilament, 100.0))
                    .thenReturn(List.of(new AllocationResult(spool, 100.0, BigDecimal.valueOf(2.50))));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(printAllocationPreviewService).updateActualUsage(
                    eq(job), eq(hubFilament), eq(100.0), anyList());
            verify(printAllocationFinalizeService).finalizeAllocation(job);

            verify(printJobRepository).save(jobCaptor.capture());
            assertThat(jobCaptor.getValue().getStatus()).isEqualTo(PrintJobStatus.CANCELLED);
            assertThat(jobCaptor.getValue().getProgress()).isEqualTo(50);
            assertThat(jobCaptor.getValue().getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("marks failed when error code is not user cancel")
        void failedByError() {
            printerState.setProgress(50);
            printerState.setLayer(80);
            printerState.setTotalLayers(160);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(123456, "Filament jam"));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(spoolAllocationService.previewAllocation(hubFilament, 100.0))
                    .thenReturn(List.of(new AllocationResult(spool, 100.0, BigDecimal.valueOf(2.50))));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(printJobRepository).save(jobCaptor.capture());
            assertThat(jobCaptor.getValue().getStatus()).isEqualTo(PrintJobStatus.FAILED);
        }

        @Test
        @DisplayName("skips cancel when job already in terminal state")
        void skipsWhenTerminal() {
            job.setStatus(PrintJobStatus.FINISHED);
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));

            printJobService.cancel(printerState);

            verifyNoInteractions(spoolAllocationService, printAllocationPreviewService, printAllocationFinalizeService);
        }

        @Test
        @DisplayName("skips filament processing when no preview exists")
        void skipsWhenNoPreview() {
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, "User cancelled"));
            printerState.setLayer(80);
            printerState.setTotalLayers(160);
            printerState.setProgress(50);

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.empty());
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verifyNoInteractions(spoolAllocationService, printAllocationPreviewService);
            verify(printAllocationFinalizeService).finalizeAllocation(job);
            verify(printJobRepository).save(jobCaptor.capture());
            assertThat(jobCaptor.getValue().getStatus()).isEqualTo(PrintJobStatus.CANCELLED);
        }

    }

    @Nested
    @DisplayName("estimateInterruptedUsage")
    class EstimateInterruptedUsage {

        private PrintAllocationGroup group;
        private PrintAllocationPreview preview;

        @BeforeEach
        void setUp() {
            group = PrintAllocationGroup.builder()
                    .id(1L).filament(hubFilament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(PLANNED_GRAMS).allocatedGrams(PLANNED_GRAMS).missingGrams(0.0)
                    .userOverridden(false).amsSlot(0).build();
            preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false).groups(List.of(group)).build();
            group.setPreview(preview);
        }

        @Test
        @DisplayName("half layers used = half grams")
        void halfLayers() {
            printerState.setLayer(80);
            printerState.setTotalLayers(160);
            printerState.setProgress(50);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, ""));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(spoolAllocationService).previewAllocation(hubFilament, 100.0);
        }

        @Test
        @DisplayName("full layers = full grams")
        void fullLayers() {
            printerState.setLayer(160);
            printerState.setTotalLayers(160);
            printerState.setProgress(100);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, ""));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(spoolAllocationService).previewAllocation(hubFilament, PLANNED_GRAMS);
        }

        @Test
        @DisplayName("clamps ratio to 1.0")
        void clampsRatio() {
            printerState.setLayer(200);
            printerState.setTotalLayers(160);
            printerState.setProgress(150);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, ""));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(spoolAllocationService).previewAllocation(hubFilament, PLANNED_GRAMS);
        }

        @Test
        @DisplayName("uses progress ratio when totalLayers is zero")
        void usesProgressRatio() {
            printerState.setLayer(0);
            printerState.setTotalLayers(0);
            printerState.setProgress(30);
            printerState.setFilaments(List.of(filamentDto));
            printerState.setError(new PrinterError(50348044, ""));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(spoolAllocationService).previewAllocation(hubFilament, 60.0);
        }

        @Test
        @DisplayName("estimates with 40/160 layer ratio")
        void fortyOf160Layers() {
            org.spon.edol.model.Filament dto =
                    new org.spon.edol.model.Filament(5, "Pbec608f", "PLA", "#F95D73",
                            "JAMG HE", "JAMG HE PLA Matte", 10.87, PLANNED_GRAMS, true, false, 0);

            printerState.setLayer(40);
            printerState.setTotalLayers(160);
            printerState.setProgress(50);
            printerState.setFilaments(List.of(dto));
            printerState.setError(new PrinterError(50348044, ""));

            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.cancel(printerState);

            verify(spoolAllocationService).previewAllocation(hubFilament, 50.0);
        }

    }

    @Nested
    @DisplayName("updateProgress")
    class UpdateProgress {

        @Test
        @DisplayName("updates progress for running job")
        void updatesRunningJob() {
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printerState.setProgress(50);
            printerState.setLayer(80);
            printerState.setTotalLayers(160);
            printerState.setRemainingTime(45);

            printJobService.updateProgress(printerState);

            verify(printJobRepository).save(jobCaptor.capture());
            PrintJob saved = jobCaptor.getValue();
            assertThat(saved.getProgress()).isEqualTo(50);
            assertThat(saved.getCurrentLayer()).isEqualTo(80);
            assertThat(saved.getTotalLayers()).isEqualTo(160);
            assertThat(saved.getRemainingTime()).isEqualTo(45);
        }

        @Test
        @DisplayName("skips update for finished jobs")
        void skipsFinishedJob() {
            job.setStatus(PrintJobStatus.FINISHED);
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));

            printJobService.updateProgress(printerState);

            verifyNoInteractions(runtimeStateService);
        }

        @Test
        @DisplayName("sets current job when null")
        void setsCurrentJobWhenNull() {
            when(printJobRepository.findBySessionId("SESS-001")).thenReturn(Optional.of(job));
            when(runtimeStateService.getCurrentJob()).thenReturn(null);
            when(printJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            printJobService.updateProgress(printerState);

            verify(runtimeStateService).setCurrentJob(job);
        }

    }

    @Nested
    @DisplayName("getJobs")
    class GetJobs {

        @Test
        @DisplayName("returns paged jobs ordered by startedAt desc")
        void returnsPagedJobs() {
            printJobService.getJobs(0, 10);

            verify(printJobRepository).findAllByOrderByStartedAtDesc(any());
        }

    }

}