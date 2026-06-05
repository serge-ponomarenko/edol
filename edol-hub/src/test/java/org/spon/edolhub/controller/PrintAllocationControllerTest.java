package org.spon.edolhub.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.AllocationSpoolOptionDto;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.service.spool.AllocationMutationService;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.spon.edolhub.service.spool.PrintAllocationReconciliationService;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintAllocationControllerTest {

    @Mock
    private PrintAllocationPreviewRepository previewRepository;

    @Mock
    private PrintAllocationPreviewMapper previewMapper;

    @Mock
    private AllocationMutationService allocationMutationService;

    @Mock
    private PrintAllocationReconciliationService printAllocationReconciliationService;

    @Mock
    private FilamentRepository filamentRepository;

    @Mock
    private FilamentSpoolRepository filamentSpoolRepository;

    @Mock
    private Model model;

    @InjectMocks
    private PrintAllocationController controller;

    @Nested
    @DisplayName("allocationPage")
    class AllocationPage {

        @Test
        @DisplayName("returns allocation view with model attributes")
        void returnsView() {
            String view = controller.allocationPage(1L, model);

            assertThat(view).isEqualTo("dashboard/print-jobs/allocation");
            verify(model).addAttribute("currentPath", "/print-jobs");
            verify(model).addAttribute("jobId", 1L);
        }
    }

    @Nested
    @DisplayName("getAllocation")
    class GetAllocation {

        @Test
        @DisplayName("returns preview DTO")
        void returnsPreview() {
            PrintAllocationPreview preview = new PrintAllocationPreview();
            PrintAllocationPreviewDto dto = new PrintAllocationPreviewDto();

            when(previewRepository.findByPrintJobId(1L)).thenReturn(Optional.of(preview));
            when(previewMapper.toDto(preview)).thenReturn(dto);

            PrintAllocationPreviewDto result = controller.getAllocation(1L);

            assertThat(result).isSameAs(dto);
        }

        @Test
        @DisplayName("throws when not found")
        void throwsWhenNotFound() {
            when(previewRepository.findByPrintJobId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getAllocation(99L))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("rerunAllocation")
    class RerunAllocation {

        @Test
        @DisplayName("calls mutation service")
        void rerunsAllocation() {
            controller.rerunAllocation(1L, 2L);

            verify(allocationMutationService).rerunAllocation(1L, 2L);
        }
    }

    @Nested
    @DisplayName("finalizeReconciliation")
    class FinalizeReconciliation {

        @Test
        @DisplayName("returns true when finalized")
        void returnsTrue() {
            when(printAllocationReconciliationService.finalizeReconciliation(1L)).thenReturn(true);

            Boolean result = controller.finalizeReconciliation(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when unresolved")
        void returnsFalse() {
            when(printAllocationReconciliationService.finalizeReconciliation(1L)).thenReturn(false);

            Boolean result = controller.finalizeReconciliation(1L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("filaments")
    class Filaments {

        @Test
        @DisplayName("returns filtered filaments up to 20")
        void returnsFiltered() {
            Filament f1 = new Filament();
            f1.setFullId("JAMG HE PLA Matte");
            Filament f2 = new Filament();
            f2.setFullId("JAMG HE PETG Basic");
            Filament f3 = new Filament();
            f3.setFullId("Other Brand ABS");

            when(filamentRepository.findAll()).thenReturn(List.of(f1, f2, f3));

            List<Filament> result = controller.filaments("jamg");

            assertThat(result)
                    .hasSize(2)
                    .containsExactly(f1, f2);
        }

        @Test
        @DisplayName("returns empty list when no match")
        void returnsEmpty() {
            when(filamentRepository.findAll()).thenReturn(List.of());

            List<Filament> result = controller.filaments("xyz");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("limits results to 20")
        void limitsTo20() {
            List<Filament> manyFilaments = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
                Filament f = new Filament();
                f.setFullId("Match " + i);
                manyFilaments.add(f);
            }

            when(filamentRepository.findAll()).thenReturn(manyFilaments);

            List<Filament> result = controller.filaments("Match");

            assertThat(result).hasSize(20);
        }
    }

    @Nested
    @DisplayName("replaceFilament")
    class ReplaceFilament {

        @Test
        @DisplayName("looks up filament and replaces")
        void replacesFilament() {
            Filament target = new Filament();
            target.setId(2L);
            when(filamentRepository.findById(2L)).thenReturn(Optional.of(target));

            controller.replaceFilament(1L, 3L, 2L);

            verify(allocationMutationService).replaceFilament(1L, 3L, target);
        }

        @Test
        @DisplayName("throws when filament not found")
        void throwsWhenNotFound() {
            when(filamentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.replaceFilament(1L, 3L, 99L))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("spools")
    class Spools {

        @Test
        @DisplayName("returns spool options for filament")
        void returnsSpoolOptions() {
            Filament filament = new Filament();
            filament.setId(1L);
            filament.setFullId("Test Filament");

            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            spool.setFilament(filament);
            spool.setWeightTotal(1000.0);
            spool.setWeightRemaining(500.0);
            spool.setPrice(BigDecimal.valueOf(25.00));
            spool.setStatus(FilamentSpool.FilamentSpoolStatus.ACTIVE);

            when(filamentSpoolRepository.findAllByFilamentIdAndStatusIn(
                    eq(1L), anyList())).thenReturn(List.of(spool));

            List<AllocationSpoolOptionDto> result = controller.spools(1L);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getId()).isEqualTo(1L);
            assertThat(result.getFirst().getWeightTotal()).isEqualTo(1000.0);
            assertThat(result.getFirst().getWeightRemaining()).isEqualTo(500.0);
            assertThat(result.getFirst().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        }
    }

    @Nested
    @DisplayName("replaceSpool")
    class ReplaceSpool {

        private FilamentSpool spool;
        private Filament filament;

        @BeforeEach
        void setUp() {
            filament = new Filament();
            filament.setId(1L);
            spool = new FilamentSpool();
            spool.setId(10L);
            spool.setFilament(filament);
            spool.setWeightTotal(1000.0);
            spool.setWeightRemaining(500.0);
            spool.setPrice(BigDecimal.valueOf(25.00));
        }

        @Test
        @DisplayName("replaces spool allocation")
        void replacesSpool() {
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            controller.replaceSpool(1L, 1L, 10L, 200.0);

            verify(allocationMutationService).replaceAllocationWithSingleSpool(
                    eq(1L), eq(1L), eq(spool), eq(200.0), any(BigDecimal.class));
        }

        @Test
        @DisplayName("throws when grams is zero")
        void throwsWhenZeroGrams() {
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.replaceSpool(1L, 1L, 10L, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("throws when grams is negative")
        void throwsWhenNegativeGrams() {
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.replaceSpool(1L, 1L, 10L, -1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("throws when spool filament does not match")
        void throwsWhenFilamentMismatch() {
            Filament otherFilament = new Filament();
            otherFilament.setId(2L);
            spool.setFilament(otherFilament);

            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.replaceSpool(1L, 1L, 10L, 200.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match");
        }

        @Test
        @DisplayName("throws when grams exceeds available weight")
        void throwsWhenExceedsWeight() {
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.replaceSpool(1L, 1L, 10L, 600.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds spool remaining");
        }
    }

    @Nested
    @DisplayName("addSpool")
    class AddSpool {

        private FilamentSpool spool;
        private Filament filament;

        @BeforeEach
        void setUp() {
            filament = new Filament();
            filament.setId(1L);
            spool = new FilamentSpool();
            spool.setId(10L);
            spool.setFilament(filament);
            spool.setWeightTotal(1000.0);
            spool.setWeightRemaining(500.0);
            spool.setPrice(BigDecimal.valueOf(25.00));
        }

        @Test
        @DisplayName("adds spool allocation")
        void addsSpool() {
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            controller.addSpool(1L, 1L, 10L, 200.0);

            verify(allocationMutationService).addAllocationItem(
                    eq(1L), eq(1L), eq(spool), eq(200.0), any(BigDecimal.class));
        }

        @Test
        @DisplayName("throws when spool not found")
        void throwsWhenSpoolNotFound() {
            when(filamentSpoolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.addSpool(1L, 1L, 99L, 200.0))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("throws when spool filament is null")
        void throwsWhenFilamentNull() {
            spool.setFilament(null);
            when(filamentSpoolRepository.findById(10L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.addSpool(1L, 1L, 10L, 200.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match");
        }
    }
}
