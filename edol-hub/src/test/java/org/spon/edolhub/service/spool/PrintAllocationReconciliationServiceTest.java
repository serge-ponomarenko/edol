package org.spon.edolhub.service.spool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.JobSpoolUsageRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.JobSpoolUsageService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintAllocationReconciliationServiceTest {

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrintAllocationFinalizeService finalizeService;

    @Mock
    private JobSpoolUsageService jobSpoolUsageService;

    @Mock
    private SpoolConsumptionService spoolConsumptionService;

    @Mock
    private JobSpoolUsageRepository jobSpoolUsageRepository;

    @Mock
    private AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @InjectMocks
    private PrintAllocationReconciliationService reconciliationService;

    private PrintJob job;
    private Vendor vendor;
    private MaterialType materialType;
    private Filament filament;
    private FilamentSpool spool;

    @BeforeEach
    void setUp() {
        vendor = Vendor.builder().id(1L).name("JAMG HE").build();
        materialType = MaterialType.builder().id(1L).name("PLA").build();
        filament = Filament.builder()
                .id(1L).fullId("JAMG HE PLA Matte").vendor(vendor)
                .materialType(materialType).brand("Matte").colorHex("#F95D73")
                .build();
        spool = FilamentSpool.builder()
                .id(1L).filament(filament).weightTotal(1000.0)
                .weightRemaining(500.0).price(BigDecimal.valueOf(25.00))
                .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                .build();
        job = PrintJob.builder()
                .id(100L).sessionId("SESS-001").fileName("test.gcode")
                .taskName("Test Print").status(PrintJobStatus.RUNNING)
                .build();
    }

    @Nested
    @DisplayName("finalizeReconciliation")
    class FinalizeReconciliation {

        @Test
        @DisplayName("finalizes when preview is not finalized")
        void finalizesUnfinalizedPreview() {
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false).groups(new ArrayList<>())
                    .build();
            when(printJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(finalizeService.finalizeAllocation(job)).thenReturn(true);

            boolean result = reconciliationService.finalizeReconciliation(job.getId());

            assertThat(result).isTrue();
            verify(finalizeService).finalizeAllocation(job);
        }

        @Test
        @DisplayName("returns true when already finalized")
        void alreadyFinalized() {
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(true).groups(new ArrayList<>())
                    .build();
            when(printJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            boolean result = reconciliationService.finalizeReconciliation(job.getId());

            assertThat(result).isTrue();
            verifyNoInteractions(finalizeService);
        }

        @Test
        @DisplayName("throws when print job not found")
        void throwsWhenJobNotFound() {
            when(printJobRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciliationService.finalizeReconciliation(999L))
                    .isInstanceOf(RuntimeException.class);
            verifyNoInteractions(previewRepository, finalizeService);
        }

        @Test
        @DisplayName("throws when preview not found")
        void throwsWhenPreviewNotFound() {
            Long jobId = job.getId();

            when(printJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciliationService.finalizeReconciliation(jobId))
                    .isInstanceOf(RuntimeException.class);
            verifyNoInteractions(finalizeService);
        }

    }

    @Nested
    @DisplayName("rollbackFinalizedAllocation")
    class RollbackFinalizedAllocation {

        @Test
        @DisplayName("rolls back finalized preview")
        void rollsBack() {
            JobSpoolUsage usage = JobSpoolUsage.builder()
                    .id(200L).printJob(job).filamentSpool(spool).usedGrams(150.0)
                    .cost(BigDecimal.valueOf(3.75)).build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(true).groups(new ArrayList<>())
                    .build();
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(jobSpoolUsageService.findByPrintJob(job.getId())).thenReturn(List.of(usage));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            reconciliationService.rollbackFinalizedAllocation(job.getId());

            verify(spoolConsumptionService).rollback(List.of(usage));
            verify(jobSpoolUsageRepository).deleteAll(List.of(usage));
            assertThat(preview.getFinalized()).isFalse();
            verify(previewRepository).save(preview);
            verify(allocationPreviewRuntimeSyncService).refresh(job.getId());
        }

        @Test
        @DisplayName("returns early when preview is not finalized")
        void notFinalizedDoesNothing() {
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false).groups(new ArrayList<>())
                    .build();
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            reconciliationService.rollbackFinalizedAllocation(job.getId());

            verifyNoInteractions(
                    jobSpoolUsageService, spoolConsumptionService,
                    jobSpoolUsageRepository, allocationPreviewRuntimeSyncService
            );
        }

        @Test
        @DisplayName("rolls back with no usages")
        void rollsBackWithNoUsages() {
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(true).groups(new ArrayList<>())
                    .build();
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(jobSpoolUsageService.findByPrintJob(job.getId())).thenReturn(List.of());
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            reconciliationService.rollbackFinalizedAllocation(job.getId());

            verify(spoolConsumptionService).rollback(List.of());
            verify(jobSpoolUsageRepository).deleteAll(List.of());
            assertThat(preview.getFinalized()).isFalse();
            verify(previewRepository).save(preview);
            verify(allocationPreviewRuntimeSyncService).refresh(job.getId());
        }

        @Test
        @DisplayName("throws when preview not found")
        void throwsWhenPreviewNotFound() {
            when(previewRepository.findByPrintJobId(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciliationService.rollbackFinalizedAllocation(999L))
                    .isInstanceOf(RuntimeException.class);
            verifyNoInteractions(
                    jobSpoolUsageService, spoolConsumptionService,
                    jobSpoolUsageRepository, allocationPreviewRuntimeSyncService
            );
        }

    }

}