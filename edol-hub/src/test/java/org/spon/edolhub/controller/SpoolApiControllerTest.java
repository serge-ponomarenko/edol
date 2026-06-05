package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpoolApiControllerTest {

    @Mock
    private FilamentSpoolRepository spoolRepository;

    @Mock
    private FilamentRepository filamentRepository;

    @InjectMocks
    private SpoolApiController controller;

    @Nested
    @DisplayName("spools")
    class Spools {

        @Test
        @DisplayName("returns all spools")
        void returnsAllSpools() {
            List<FilamentSpool> spools = List.of(new FilamentSpool(), new FilamentSpool());
            when(spoolRepository.findAll()).thenReturn(spools);

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

            when(filamentRepository.findFirstByPrinterFilamentProfileIdAndColorHexIgnoreCase("P001", "#FF0000"))
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
            when(filamentRepository.findFirstByPrinterFilamentProfileIdAndColorHexIgnoreCase("P001", "#FF0000"))
                    .thenReturn(Optional.empty());

            ResponseEntity<FilamentSpool> response = controller.findSpool("P001", "#FF0000");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        }

        @Test
        @DisplayName("returns 204 when no active spool found")
        void returns204WhenNoSpool() {
            Filament filament = new Filament();
            filament.setId(1L);

            when(filamentRepository.findFirstByPrinterFilamentProfileIdAndColorHexIgnoreCase("P001", "#FF0000"))
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
            when(spoolRepository.findById(1L)).thenReturn(Optional.of(spool));

            ResponseEntity<FilamentSpool> response = controller.findSpoolById(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isSameAs(spool);
        }

        @Test
        @DisplayName("returns 204 when not found")
        void returns204WhenNotFound() {
            when(spoolRepository.findById(99L)).thenReturn(Optional.empty());

            ResponseEntity<FilamentSpool> response = controller.findSpoolById(99L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        }
    }
}
