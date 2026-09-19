package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.TenantContext;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpoolApiControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private FilamentSpoolRepository spoolRepository;

    @Mock
    private FilamentRepository filamentRepository;

    @Mock
    private TenantContext tenantContext;

    @InjectMocks
    private SpoolApiController controller;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
    }

    @Nested
    @DisplayName("spools")
    class Spools {

        @Test
        @DisplayName("returns all spools")
        void returnsAllSpools() {
            List<FilamentSpool> spools = List.of(new FilamentSpool(), new FilamentSpool());
            when(spoolRepository.findAllByFilamentTenantId(TENANT_ID)).thenReturn(spools);

            List<FilamentSpool> result = controller.spools();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findSpool")
    class FindSpool {

        @Test
        @DisplayName("returns spool when filament and active spool found")
        void returnsSpool() {
            Filament filament = new Filament();
            filament.setId(1L);
            FilamentSpool spool = new FilamentSpool();
            spool.setId(10L);

            when(filamentRepository.findFirstByTenantIdAndPrinterFilamentProfileIdAndColorHexIgnoreCase(TENANT_ID, "P001", "#FF0000"))
                    .thenReturn(Optional.of(filament));
            when(spoolRepository.findFirstByFilamentIdAndStatus(1L, FilamentSpool.FilamentSpoolStatus.ACTIVE))
                    .thenReturn(Optional.of(spool));

            ResponseEntity<FilamentSpool> response = controller.findSpool("P001", "#FF0000");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isSameAs(spool);
        }

        @Test
        @DisplayName("returns 204 when no filament found")
        void returns204WhenNoFilament() {
            when(filamentRepository.findFirstByTenantIdAndPrinterFilamentProfileIdAndColorHexIgnoreCase(TENANT_ID, "P001", "#FF0000"))
                    .thenReturn(Optional.empty());

            ResponseEntity<FilamentSpool> response = controller.findSpool("P001", "#FF0000");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        }

        @Test
        @DisplayName("returns 204 when no active spool found")
        void returns204WhenNoSpool() {
            Filament filament = new Filament();
            filament.setId(1L);

            when(filamentRepository.findFirstByTenantIdAndPrinterFilamentProfileIdAndColorHexIgnoreCase(TENANT_ID, "P001", "#FF0000"))
                    .thenReturn(Optional.of(filament));
            when(spoolRepository.findFirstByFilamentIdAndStatus(1L, FilamentSpool.FilamentSpoolStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            ResponseEntity<FilamentSpool> response = controller.findSpool("P001", "#FF0000");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        }
    }

    @Nested
    @DisplayName("findSpoolById")
    class FindSpoolById {

        @Test
        @DisplayName("returns spool when found")
        void returnsSpool() {
            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            when(spoolRepository.findByIdAndFilamentTenantId(1L, TENANT_ID)).thenReturn(Optional.of(spool));

            ResponseEntity<FilamentSpool> response = controller.findSpoolById(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isSameAs(spool);
        }

        @Test
        @DisplayName("returns 204 when not found")
        void returns204WhenNotFound() {
            when(spoolRepository.findByIdAndFilamentTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

            ResponseEntity<FilamentSpool> response = controller.findSpoolById(99L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        }
    }
}
