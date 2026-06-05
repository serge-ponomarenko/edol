package org.spon.edolhub.service.spool;

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
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.JobSpoolUsageService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintAllocationFinalizeServiceTest {

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private JobSpoolUsageService jobSpoolUsageService;

    @Mock
    private SpoolConsumptionService spoolConsumptionService;

    @InjectMocks
    private PrintAllocationFinalizeService finalizeService;

    @Captor
    private ArgumentCaptor<List<JobSpoolUsage>> usageCaptor;

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
    @DisplayName("finalizeAllocation")
    class FinalizeAllocation {

        @Test
        @DisplayName("returns false when no preview exists")
        void noPreview() {
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.empty());

            boolean result = finalizeService.finalizeAllocation(job);

            assertThat(result).isFalse();
            verifyNoInteractions(jobSpoolUsageService, spoolConsumptionService);
        }

        @Test
        @DisplayName("returns true when already finalized")
        void alreadyFinalized() {
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(true).groups(new ArrayList<>())
                    .build();
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            boolean result = finalizeService.finalizeAllocation(job);

            assertThat(result).isTrue();
            verifyNoInteractions(jobSpoolUsageService, spoolConsumptionService);
        }

        @Test
        @DisplayName("returns false when groups are unresolved")
        void unresolvedGroups() {
            PrintAllocationGroup unresolvedGroup = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.PARTIAL)
                    .requestedGrams(200.0).allocatedGrams(100.0).missingGrams(100.0)
                    .userOverridden(false).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(unresolvedGroup)))
                    .build();
            unresolvedGroup.setPreview(preview);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            boolean result = finalizeService.finalizeAllocation(job);

            assertThat(result).isFalse();
            verifyNoInteractions(jobSpoolUsageService, spoolConsumptionService);
        }

        @Test
        @DisplayName("finalizes single resolved group and deducts inventory")
        void singleResolvedGroup() {
            PrintAllocationItem item = PrintAllocationItem.builder()
                    .id(1L).spool(spool).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(5.00)).userSelected(false)
                    .build();
            PrintAllocationGroup resolvedGroup = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>(List.of(item)))
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(resolvedGroup)))
                    .build();
            resolvedGroup.setPreview(preview);
            item.setGroup(resolvedGroup);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            JobSpoolUsage usage = JobSpoolUsage.builder()
                    .id(200L).printJob(job).filamentSpool(spool)
                    .usedGrams(0.0).cost(null).build();
            when(jobSpoolUsageService.create(job, spool)).thenReturn(usage);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = finalizeService.finalizeAllocation(job);

            assertThat(result).isTrue();
            assertThat(preview.getFinalized()).isTrue();
            assertThat(usage.getUsedGrams()).isEqualTo(200.0);
            assertThat(usage.getCost()).isEqualByComparingTo(BigDecimal.valueOf(5.00));

            verify(jobSpoolUsageService).create(job, spool);
            verify(spoolConsumptionService).consume(usageCaptor.capture());
            assertThat(usageCaptor.getValue()).hasSize(1);
            assertThat(usageCaptor.getValue().getFirst()).isEqualTo(usage);
            verify(previewRepository).save(preview);
        }

        @Test
        @DisplayName("finalizes multiple groups with multiple items each")
        void multipleGroupsAndItems() {
            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L).filament(filament).weightTotal(1000.0)
                    .weightRemaining(500.0).price(BigDecimal.valueOf(30.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();
            FilamentSpool spool3 = FilamentSpool.builder()
                    .id(3L).filament(filament).weightTotal(1000.0)
                    .weightRemaining(300.0).price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();
            Filament filament2 = Filament.builder()
                    .id(2L).fullId("JAMG HE PETG Basic").vendor(vendor)
                    .materialType(MaterialType.builder().id(2L).name("PETG").build())
                    .brand("Basic").colorHex("#161616").build();

            PrintAllocationItem item1 = PrintAllocationItem.builder()
                    .id(1L).spool(spool).allocatedGrams(150.0)
                    .estimatedCost(BigDecimal.valueOf(3.75)).userSelected(false)
                    .build();
            PrintAllocationItem item2 = PrintAllocationItem.builder()
                    .id(2L).spool(spool3).allocatedGrams(50.0)
                    .estimatedCost(BigDecimal.valueOf(1.25)).userSelected(false)
                    .build();
            PrintAllocationItem item3 = PrintAllocationItem.builder()
                    .id(3L).spool(spool2).allocatedGrams(180.0)
                    .estimatedCost(BigDecimal.valueOf(5.40)).userSelected(false)
                    .build();

            PrintAllocationGroup group1 = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>(List.of(item1, item2)))
                    .build();
            PrintAllocationGroup group2 = PrintAllocationGroup.builder()
                    .id(2L).filament(filament2).status(AllocationStatus.RESOLVED)
                    .requestedGrams(180.0).allocatedGrams(180.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>(List.of(item3)))
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(group1, group2)))
                    .build();
            group1.setPreview(preview);
            group2.setPreview(preview);
            item1.setGroup(group1);
            item2.setGroup(group1);
            item3.setGroup(group2);

            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            JobSpoolUsage usage1 = JobSpoolUsage.builder().id(200L).printJob(job).filamentSpool(spool).usedGrams(0.0).build();
            JobSpoolUsage usage2 = JobSpoolUsage.builder().id(201L).printJob(job).filamentSpool(spool2).usedGrams(0.0).build();
            JobSpoolUsage usage3 = JobSpoolUsage.builder().id(202L).printJob(job).filamentSpool(spool3).usedGrams(0.0).build();
            when(jobSpoolUsageService.create(job, spool)).thenReturn(usage1);
            when(jobSpoolUsageService.create(job, spool2)).thenReturn(usage2);
            when(jobSpoolUsageService.create(job, spool3)).thenReturn(usage3);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = finalizeService.finalizeAllocation(job);

            assertThat(result).isTrue();
            assertThat(preview.getFinalized()).isTrue();
            assertThat(usage1.getUsedGrams()).isEqualTo(150.0);
            assertThat(usage2.getUsedGrams()).isEqualTo(180.0);
            assertThat(usage3.getUsedGrams()).isEqualTo(50.0);

            verify(spoolConsumptionService).consume(usageCaptor.capture());
            assertThat(usageCaptor.getValue()).hasSize(3);
        }

        @Test
        @DisplayName("marks preview as finalized only once")
        void multipleCallsReturnTrueAfterFinalized() {
            PrintAllocationItem item = PrintAllocationItem.builder()
                    .id(1L).spool(spool).allocatedGrams(100.0)
                    .estimatedCost(BigDecimal.valueOf(2.50)).userSelected(false)
                    .build();
            PrintAllocationGroup resolvedGroup = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(100.0).allocatedGrams(100.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>(List.of(item)))
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(resolvedGroup)))
                    .build();
            resolvedGroup.setPreview(preview);
            item.setGroup(resolvedGroup);

            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            JobSpoolUsage usage = JobSpoolUsage.builder().id(200L).printJob(job).filamentSpool(spool).usedGrams(0.0).build();
            when(jobSpoolUsageService.create(job, spool)).thenReturn(usage);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean firstCall = finalizeService.finalizeAllocation(job);
            assertThat(firstCall).isTrue();
            assertThat(preview.getFinalized()).isTrue();

            verify(spoolConsumptionService).consume(any());

            boolean secondCall = finalizeService.finalizeAllocation(job);
            assertThat(secondCall).isTrue();
        }

    }

}