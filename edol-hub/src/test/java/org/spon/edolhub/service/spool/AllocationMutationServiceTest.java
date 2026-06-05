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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationMutationServiceTest {

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private AllocationResultMapper allocationResultMapper;

    @Mock
    private SpoolAllocationService spoolAllocationService;

    @Mock
    private PrintAllocationReconciliationService reconciliationService;

    @Mock
    private AllocationPreviewRuntimeSyncService allocationPreviewRuntimeSyncService;

    @InjectMocks
    private AllocationMutationService mutationService;

    @Captor
    private ArgumentCaptor<PrintAllocationPreview> previewCaptor;

    private PrintJob job;
    private Filament filament;
    private Vendor vendor;
    private MaterialType materialType;
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

    private PrintAllocationGroup groupWithFilament(Filament f, double requested, AllocationStatus status, List<PrintAllocationItem> items) {
        return PrintAllocationGroup.builder()
                .id(1L).filament(f).status(status)
                .requestedGrams(requested).allocatedGrams(0.0).missingGrams(requested)
                .userOverridden(false).items(new ArrayList<>(items != null ? items : List.of()))
                .build();
    }

    private PrintAllocationItem item(FilamentSpool s, double grams, BigDecimal cost) {
        return PrintAllocationItem.builder()
                .id(1L).spool(s).allocatedGrams(grams)
                .estimatedCost(cost).userSelected(false)
                .build();
    }

    private PrintAllocationPreview previewWithGroup(PrintAllocationGroup group, boolean finalized) {
        PrintAllocationPreview preview = PrintAllocationPreview.builder()
                .id(10L).printJob(job).finalized(finalized)
                .groups(new ArrayList<>(List.of(group)))
                .build();
        group.setPreview(preview);
        return preview;
    }

    @Nested
    @DisplayName("replaceAllocationWithSingleSpool")
    class ReplaceAllocationWithSingleSpool {

        @Test
        @DisplayName("replaces items with single user-selected spool")
        void replacesWithSingleSpool() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.PARTIAL, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(5.00)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(any(), any())).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.replaceAllocationWithSingleSpool(job.getId(), filament.getId(), spool, 200.0, BigDecimal.valueOf(5.00));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getUserOverridden()).isTrue();
            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().getFirst().getUserSelected()).isTrue();
            assertThat(saved.getAllocatedGrams()).isEqualTo(200.0);
            assertThat(saved.getMissingGrams()).isEqualTo(0.0);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
            verify(allocationPreviewRuntimeSyncService).refresh(job.getId());
        }

        @Test
        @DisplayName("sets PARTIAL when allocation is less than requested")
        void partialWhenUnderAllocated() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.PARTIAL, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool).allocatedGrams(150.0)
                    .estimatedCost(BigDecimal.valueOf(3.75)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(any(), any())).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.replaceAllocationWithSingleSpool(job.getId(), filament.getId(), spool, 150.0, BigDecimal.valueOf(3.75));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getAllocatedGrams()).isEqualTo(150.0);
            assertThat(saved.getMissingGrams()).isEqualTo(50.0);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.PARTIAL);
        }

        @Test
        @DisplayName("throws when allocating more than requested")
        void throwsWhenExceedsRequested() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            Long filamentId = filament.getId();

            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            assertThatThrownBy(() ->
                    mutationService.replaceAllocationWithSingleSpool(jobId, filamentId, spool, 250.0, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Allocation exceeds requested job grams");
        }

        @Test
        @DisplayName("throws when grams are null")
        void throwsWhenGramsNull() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            Long filamentId = filament.getId();

            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            assertThatThrownBy(() ->
                    mutationService.replaceAllocationWithSingleSpool(jobId, filamentId, spool, null, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Allocation grams must be greater than zero");
        }

        @Test
        @DisplayName("throws when grams are zero")
        void throwsWhenGramsZero() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            Long filamentId = filament.getId();

            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            assertThatThrownBy(() ->
                    mutationService.replaceAllocationWithSingleSpool(jobId, filamentId, spool, 0.0, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Allocation grams must be greater than zero");
        }

        @Test
        @DisplayName("rolls back finalized preview before mutating")
        void rollsBackFinalizedPreview() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, null);
            PrintAllocationPreview preview = previewWithGroup(group, true);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(5.00)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(any(), any())).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.replaceAllocationWithSingleSpool(job.getId(), filament.getId(), spool, 200.0, BigDecimal.valueOf(5.00));

            verify(reconciliationService).rollbackFinalizedAllocation(job.getId());
        }

    }

    @Nested
    @DisplayName("addAllocationItem")
    class AddAllocationItem {

        @Test
        @DisplayName("adds item to existing group")
        void addsItem() {
            PrintAllocationItem existing = item(spool, 100.0, BigDecimal.valueOf(2.50));
            existing.setUserSelected(false);
            PrintAllocationGroup group = groupWithFilament(filament, 250.0, AllocationStatus.PARTIAL, List.of(existing));
            group.setAllocatedGrams(100.0);
            group.setMissingGrams(150.0);
            existing.setGroup(group);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L).filament(filament).weightTotal(1000.0)
                    .weightRemaining(500.0).price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();
            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool2).allocatedGrams(100.0)
                    .estimatedCost(BigDecimal.valueOf(2.50)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(any(), any())).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.addAllocationItem(job.getId(), filament.getId(), spool2, 100.0, BigDecimal.valueOf(2.50));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getItems()).hasSize(2);
            assertThat(saved.getUserOverridden()).isTrue();
            assertThat(saved.getAllocatedGrams()).isEqualTo(200.0);
            assertThat(saved.getMissingGrams()).isEqualTo(50.0);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.PARTIAL);
        }

        @Test
        @DisplayName("marks RESOLVED when allocation fills remaining")
        void resolvesWhenFilled() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.PARTIAL, new ArrayList<>());
            group.setAllocatedGrams(0.0);
            group.setMissingGrams(200.0);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(5.00)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(any(), any())).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.addAllocationItem(job.getId(), filament.getId(), spool, 200.0, BigDecimal.valueOf(5.00));

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getAllocatedGrams()).isEqualTo(200.0);
            assertThat(saved.getMissingGrams()).isEqualTo(0.0);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
        }

        @Test
        @DisplayName("throws when spool already allocated in group")
        void throwsWhenSpoolDuplicate() {
            PrintAllocationItem existing = item(spool, 100.0, BigDecimal.valueOf(2.50));
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.PARTIAL, List.of(existing));
            group.setAllocatedGrams(100.0);
            group.setMissingGrams(100.0);
            existing.setGroup(group);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            Long filamentId = filament.getId();

            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            assertThatThrownBy(() ->
                    mutationService.addAllocationItem(jobId, filamentId, spool, 50.0, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Spool is already allocated in this group");
        }

        @Test
        @DisplayName("throws when allocation exceeds remaining grams")
        void throwsWhenExceedsRemaining() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.PARTIAL, new ArrayList<>());
            group.setAllocatedGrams(0.0);
            group.setMissingGrams(200.0);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            Long filamentId = filament.getId();

            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L).filament(filament).weightTotal(1000.0)
                    .weightRemaining(500.0).price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            assertThatThrownBy(() ->
                    mutationService.addAllocationItem(jobId, filamentId, spool2, 250.0, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Allocation exceeds remaining job grams");
        }

    }

    @Nested
    @DisplayName("rerunAllocation")
    class RerunAllocation {

        @Test
        @DisplayName("re-runs auto allocation and clears user-override")
        void rerunsAllocation() {
            PrintAllocationItem existing = item(spool, 200.0, BigDecimal.valueOf(5.00));
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, List.of(existing));
            group.setUserOverridden(true);
            existing.setGroup(group);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L).filament(filament).weightTotal(1000.0)
                    .weightRemaining(800.0).price(BigDecimal.valueOf(30.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();
            AllocationResult autoResult = new AllocationResult(spool2, 200.0, BigDecimal.valueOf(6.00));
            when(spoolAllocationService.previewAllocation(filament, 200.0)).thenReturn(List.of(autoResult));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool2).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(6.00)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(group, autoResult)).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.rerunAllocation(job.getId(), filament.getId());

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getUserOverridden()).isFalse();
            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().getFirst().getSpool().getId()).isEqualTo(2L);
            assertThat(saved.getAllocatedGrams()).isEqualTo(200.0);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
            verify(spoolAllocationService).previewAllocation(filament, 200.0);
        }

    }

    @Nested
    @DisplayName("replaceFilament")
    class ReplaceFilament {

        @Test
        @DisplayName("replaces filament and re-runs allocation")
        void replacesFilament() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, List.of(item(spool, 200.0, BigDecimal.valueOf(5.00))));
            PrintAllocationPreview preview = previewWithGroup(group, false);
            when(previewRepository.findByPrintJobId(job.getId())).thenReturn(Optional.of(preview));

            MaterialType materialType2 = MaterialType.builder().id(2L).name("PETG").build();
            Filament targetFilament = Filament.builder()
                    .id(2L).fullId("JAMG HE PETG Basic").vendor(vendor)
                    .materialType(materialType2).brand("Basic").colorHex("#161616")
                    .build();
            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L).filament(targetFilament).weightTotal(1000.0)
                    .weightRemaining(900.0).price(BigDecimal.valueOf(30.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();
            AllocationResult autoResult = new AllocationResult(spool2, 200.0, BigDecimal.valueOf(6.00));
            when(spoolAllocationService.previewAllocation(targetFilament, 200.0)).thenReturn(List.of(autoResult));

            PrintAllocationItem mappedItem = PrintAllocationItem.builder()
                    .group(group).spool(spool2).allocatedGrams(200.0)
                    .estimatedCost(BigDecimal.valueOf(6.00)).userSelected(false)
                    .build();
            when(allocationResultMapper.toItem(group, autoResult)).thenReturn(mappedItem);
            when(previewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            mutationService.replaceFilament(job.getId(), filament.getId(), targetFilament);

            verify(previewRepository).save(previewCaptor.capture());
            PrintAllocationGroup saved = previewCaptor.getValue().getGroups().getFirst();
            assertThat(saved.getFilament().getId()).isEqualTo(2L);
            assertThat(saved.getUserOverridden()).isTrue();
            assertThat(saved.getItems().getFirst().getSpool().getId()).isEqualTo(2L);
            assertThat(saved.getStatus()).isEqualTo(AllocationStatus.RESOLVED);
            verify(spoolAllocationService).previewAllocation(targetFilament, 200.0);
        }

    }

    @Nested
    @DisplayName("validation edge cases")
    class Validation {

        @Test
        @DisplayName("throws when group not found for filament id")
        void throwsWhenGroupNotFound() {
            PrintAllocationGroup group = groupWithFilament(filament, 200.0, AllocationStatus.RESOLVED, null);
            PrintAllocationPreview preview = previewWithGroup(group, false);
            Long jobId = job.getId();
            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(preview));

            assertThatThrownBy(() ->
                    mutationService.replaceAllocationWithSingleSpool(jobId, 999L, spool, 100.0, BigDecimal.ZERO))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws when preview not found")
        void throwsWhenPreviewNotFound() {
            Long jobId = job.getId();
            when(previewRepository.findByPrintJobId(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    mutationService.replaceAllocationWithSingleSpool(jobId, 1L, spool, 100.0, BigDecimal.ZERO))
                    .isInstanceOf(RuntimeException.class);
        }

    }

}