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
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.*;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintAllocationPreviewServiceTest {

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private AllocationResultMapper allocationResultMapper;

    @Mock
    private AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @InjectMocks
    private PrintAllocationPreviewService previewService;

    @Captor
    private ArgumentCaptor<PrintAllocationPreview> previewCaptor;

    private PrintJob job;
    private Filament filament;
    private Vendor vendor;
    private MaterialType materialType;

    @BeforeEach
    void setUp() {
        vendor = Vendor.builder().id(1L).name("JAMG HE").build();
        materialType = MaterialType.builder().id(1L).name("PLA").build();
        filament = Filament.builder()
                .id(1L).fullId("JAMG HE PLA Matte").vendor(vendor)
                .materialType(materialType).brand("Matte").colorHex("#F95D73")
                .build();
        job = PrintJob.builder()
                .id(100L).sessionId("SESS-001").fileName("test.gcode")
                .taskName("Test Print").status(PrintJobStatus.RUNNING)
                .build();
    }

    @Nested
    @DisplayName("createOrUpdate")
    class CreateOrUpdate {

        @Test
        @DisplayName("creates new preview with resolved group")
        void createsNewPreview() {
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.empty());

            FilamentSpool spool = spool(1L, 500.0);
            AllocationResult allocation = new AllocationResult(spool, 200.0, BigDecimal.valueOf(5.00));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PrintAllocationPreview result = previewService.createOrUpdate(job, filament, 0, 200.0, List.of(allocation));

            assertThat(result).isNotNull();
            assertThat(result.getPrintJob()).isEqualTo(job);
            assertThat(result.getFinalized()).isFalse();
            assertThat(result.getGroups()).hasSize(1);

            PrintAllocationGroup group = result.getGroups().getFirst();
            assertThat(group.getFilament()).isEqualTo(filament);
            assertThat(group.getRequestedGrams()).isEqualTo(200.0);
            assertThat(group.getAllocatedGrams()).isEqualTo(200.0);
            assertThat(group.getMissingGrams()).isEqualTo(0.0);
            assertThat(group.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
            assertThat(group.getUserOverridden()).isFalse();
            assertThat(group.getAmsSlot()).isZero();
            assertThat(group.getItems()).hasSize(1);
            assertThat(group.getItems().getFirst().getSpool()).isEqualTo(spool);
            assertThat(group.getItems().getFirst().getAllocatedGrams()).isEqualTo(200.0);

            verify(previewRepository).save(result);
            verify(allocationPreviewRuntimeSyncService).refresh(job.getId());
        }

        @Test
        @DisplayName("adds group to existing preview with different filament")
        void addsGroupToExistingPreview() {
            Filament filament2 = Filament.builder()
                    .id(2L).fullId("JAMG HE PETG Basic").vendor(vendor)
                    .materialType(materialType).brand("Basic").colorHex("#161616")
                    .build();

            PrintAllocationPreview existing = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false).groups(new ArrayList<>())
                    .build();
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(existing));

            FilamentSpool spool = spool(2L, 800.0);
            AllocationResult allocation = new AllocationResult(spool, 150.0, BigDecimal.valueOf(3.00));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PrintAllocationPreview result = previewService.createOrUpdate(job, filament2, 1, 150.0, List.of(allocation));

            assertThat(result.getGroups()).hasSize(1);
            assertThat(result.getGroups().getFirst().getFilament().getId()).isEqualTo(2L);
            assertThat(result.getGroups().getFirst().getAmsSlot()).isEqualTo(1);
        }

        @Test
        @DisplayName("replaces existing group when not user-overridden")
        void replacesExistingGroup() {
            PrintAllocationGroup existingGroup = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview existing = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(existingGroup)))
                    .build();
            existingGroup.setPreview(existing);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(existing));

            FilamentSpool spool = spool(1L, 500.0);
            AllocationResult allocation = new AllocationResult(spool, 300.0, BigDecimal.valueOf(7.50));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PrintAllocationPreview result = previewService.createOrUpdate(job, filament, 0, 300.0, List.of(allocation));

            assertThat(result.getGroups()).hasSize(1);
            assertThat(result.getGroups().getFirst().getRequestedGrams()).isEqualTo(300.0);
            assertThat(result.getGroups().getFirst().getAllocatedGrams()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("skips update when existing group is user-overridden")
        void skipsWhenUserOverridden() {
            PrintAllocationGroup existingGroup = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(true).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview existing = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(existingGroup)))
                    .build();
            existingGroup.setPreview(existing);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(existing));

            previewService.createOrUpdate(job, filament, 0, 999.0, List.of());

            assertThat(existing.getGroups()).hasSize(1);
            assertThat(existing.getGroups().getFirst().getRequestedGrams()).isEqualTo(200.0);
        }

        @Test
        @DisplayName("status is MISSING_SPOOL when allocations are empty")
        void missingSpoolWhenEmptyAllocations() {
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.empty());
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PrintAllocationPreview result = previewService.createOrUpdate(job, filament, 0, 200.0, List.of());

            assertThat(result.getGroups().getFirst().getStatus()).isEqualTo(AllocationStatus.MISSING_SPOOL);
            assertThat(result.getGroups().getFirst().getAllocatedGrams()).isEqualTo(0.0);
            assertThat(result.getGroups().getFirst().getMissingGrams()).isEqualTo(200.0);
        }

        @Test
        @DisplayName("status is PARTIAL when allocation is incomplete")
        void partialWhenIncomplete() {
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.empty());

            FilamentSpool spool = spool(1L, 100.0);
            AllocationResult allocation = new AllocationResult(spool, 100.0, BigDecimal.valueOf(2.50));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PrintAllocationPreview result = previewService.createOrUpdate(job, filament, 0, 250.0, List.of(allocation));

            assertThat(result.getGroups().getFirst().getStatus()).isEqualTo(AllocationStatus.PARTIAL);
            assertThat(result.getGroups().getFirst().getAllocatedGrams()).isEqualTo(100.0);
            assertThat(result.getGroups().getFirst().getMissingGrams()).isEqualTo(150.0);
        }

    }

    @Nested
    @DisplayName("updateActualUsage")
    class UpdateActualUsage {

        @Test
        @DisplayName("updates group with actual usage and resolves")
        void updatesWithActualUsage() {
            PrintAllocationGroup group = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(group)))
                    .build();
            group.setPreview(preview);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            FilamentSpool spool = spool(1L, 500.0);
            AllocationResult allocation = new AllocationResult(spool, 180.0, BigDecimal.valueOf(4.50));
            PrintAllocationItem item = item(null, spool, 180.0, BigDecimal.valueOf(4.50), false);
            when(allocationResultMapper.toItem(any(), any())).thenReturn(item);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            previewService.updateActualUsage(job, filament, 180.0, List.of(allocation));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup updated = previewCaptor.getValue().getGroups().getFirst();
            assertThat(updated.getRequestedGrams()).isEqualTo(180.0);
            assertThat(updated.getAllocatedGrams()).isEqualTo(180.0);
            assertThat(updated.getMissingGrams()).isEqualTo(0.0);
            assertThat(updated.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
        }

        @Test
        @DisplayName("status becomes MISSING_SPOOL when actual allocations are empty")
        void missingSpoolWhenNoAllocations() {
            PrintAllocationGroup group = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(group)))
                    .build();
            group.setPreview(preview);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            previewService.updateActualUsage(job, filament, 180.0, List.of());

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup updated = previewCaptor.getValue().getGroups().getFirst();
            assertThat(updated.getStatus()).isEqualTo(AllocationStatus.MISSING_SPOOL);
            assertThat(updated.getAllocatedGrams()).isEqualTo(0.0);
            assertThat(updated.getMissingGrams()).isEqualTo(180.0);
        }

        @Test
        @DisplayName("status becomes PARTIAL when actual usage exceeds available")
        void partialWhenUsageExceedsAvailable() {
            PrintAllocationGroup group = PrintAllocationGroup.builder()
                    .id(1L).filament(filament).status(AllocationStatus.RESOLVED)
                    .requestedGrams(200.0).allocatedGrams(200.0).missingGrams(0.0)
                    .userOverridden(false).items(new ArrayList<>())
                    .build();
            PrintAllocationPreview preview = PrintAllocationPreview.builder()
                    .id(10L).printJob(job).finalized(false)
                    .groups(new ArrayList<>(List.of(group)))
                    .build();
            group.setPreview(preview);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            FilamentSpool spool = spool(1L, 100.0);
            AllocationResult allocation = new AllocationResult(spool, 100.0, BigDecimal.valueOf(2.50));
            PrintAllocationItem item = item(null, spool, 100.0, BigDecimal.valueOf(2.50), false);
            when(allocationResultMapper.toItem(any(), any())).thenReturn(item);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            previewService.updateActualUsage(job, filament, 250.0, List.of(allocation));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup updated = previewCaptor.getValue().getGroups().getFirst();
            assertThat(updated.getRequestedGrams()).isEqualTo(250.0);
            assertThat(updated.getAllocatedGrams()).isEqualTo(100.0);
            assertThat(updated.getMissingGrams()).isEqualTo(150.0);
            assertThat(updated.getStatus()).isEqualTo(AllocationStatus.PARTIAL);
        }

    }

    private FilamentSpool spool(Long id, Double weightRemaining) {
        return FilamentSpool.builder()
                .id(id).filament(filament).weightTotal(1000.0)
                .weightRemaining(weightRemaining)
                .price(BigDecimal.valueOf(25.00))
                .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                .build();
    }

    private PrintAllocationItem item(Long id, FilamentSpool spool, Double grams, BigDecimal cost, boolean userSelected) {
        return PrintAllocationItem.builder()
                .id(id).spool(spool).allocatedGrams(grams)
                .estimatedCost(cost).userSelected(userSelected)
                .build();
    }

}