package org.spon.edolhub.service.spool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.model.entity.Vendor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpoolAllocationServiceTest {

    @Mock
    private SpoolResolverService spoolResolverService;

    @InjectMocks
    private SpoolAllocationService spoolAllocationService;

    private Filament filament;
    private Vendor vendor;
    private MaterialType materialType;

    @BeforeEach
    void setUp() {
        vendor = Vendor.builder()
                .id(1L)
                .name("JAMG HE")
                .build();

        materialType = MaterialType.builder()
                .id(1L)
                .name("PLA")
                .build();

        filament = Filament.builder()
                .id(1L)
                .fullId("JAMG HE PLA Matte")
                .vendor(vendor)
                .materialType(materialType)
                .brand("Matte")
                .colorHex("#F95D73")
                .build();
    }

    @Nested
    @DisplayName("previewAllocation")
    class PreviewAllocation {

        @Test
        @DisplayName("single spool covers entire estimate")
        void singleSpoolCoversEntireEstimate() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(500.0)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.0);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getSpool()).isEqualTo(spool);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(200.0);
            assertThat(results.getFirst().getCost()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
        }

        @Test
        @DisplayName("multiple spools needed to cover estimate")
        void multipleSpoolsNeeded() {
            FilamentSpool spool1 = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(100.0)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(200.0)
                    .price(BigDecimal.valueOf(30.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool1, spool2));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 250.0);

            assertThat(results).hasSize(2);
            assertThat(results.getFirst().getSpool()).isEqualTo(spool1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(100.0);
            assertThat(results.get(1).getSpool()).isEqualTo(spool2);
            assertThat(results.get(1).getAllocatedGrams()).isEqualTo(150.0);
        }

        @ParameterizedTest
        @CsvSource({
                "250.0, 250.0, 250.0",
                "50.0,  200.0,  50.0",
                "500.5678, 200.789, 200.8"
        })
        @DisplayName("allocates expected grams for given spool remaining and estimate")
        void allocatesExpectedGrams(double weightRemaining, double estimateGrams, double expectedAllocated) {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(weightRemaining)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, estimateGrams);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(expectedAllocated);
        }

        @Test
        @DisplayName("not enough filament across all spools")
        void notEnoughFilament() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(50.0)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.0);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("empty candidates returns empty list")
        void emptyCandidates() {
            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of());

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("fallback to weightTotal when weightRemaining is null")
        void fallbackToWeightTotal() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(500.0)
                    .weightRemaining(null)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.SEALED)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 300.0);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("spool with zero remaining weight is skipped")
        void zeroRemainingWeightSkipped() {
            FilamentSpool emptySpool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(0.0)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(emptySpool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("cost is zero when spool price is null")
        void costIsZeroWhenPriceNull() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(500.0)
                    .price(null)
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.0);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("cost calculation precision")
        void costCalculationPrecision() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(750.0)
                    .price(BigDecimal.valueOf(29.99))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 333.0);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(333.0);
            assertThat(results.getFirst().getCost())
                    .isEqualByComparingTo(BigDecimal.valueOf(9.9900));
        }

        @Test
        @DisplayName("multiple spools with partial fills exhaust remaining")
        void multipleSpoolsExhaustRemaining() {
            FilamentSpool spool1 = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(100.0)
                    .price(BigDecimal.valueOf(20.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            FilamentSpool spool2 = FilamentSpool.builder()
                    .id(2L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(100.0)
                    .price(BigDecimal.valueOf(20.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            FilamentSpool spool3 = FilamentSpool.builder()
                    .id(3L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(100.0)
                    .price(BigDecimal.valueOf(20.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool1, spool2, spool3));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 250.0);

            assertThat(results).hasSize(3);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(100.0);
            assertThat(results.get(1).getAllocatedGrams()).isEqualTo(100.0);
            assertThat(results.get(2).getAllocatedGrams()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("zero estimated grams returns empty allocations")
        void zeroEstimatedGrams() {
            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of());

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 0.0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("gram values are rounded to one decimal place")
        void gramValuesRounded() {
            FilamentSpool spool = FilamentSpool.builder()
                    .id(1L)
                    .filament(filament)
                    .weightTotal(1000.0)
                    .weightRemaining(500.5678)
                    .price(BigDecimal.valueOf(25.00))
                    .status(FilamentSpool.FilamentSpoolStatus.ACTIVE)
                    .build();

            when(spoolResolverService.resolveCandidates(filament))
                    .thenReturn(List.of(spool));

            List<AllocationResult> results = spoolAllocationService.previewAllocation(filament, 200.789);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAllocatedGrams()).isEqualTo(200.8);
        }

    }

}