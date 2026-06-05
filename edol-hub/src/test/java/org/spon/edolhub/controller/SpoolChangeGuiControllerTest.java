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
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpoolChangeGuiControllerTest {

    @Mock
    private FilamentSpoolRepository filamentSpoolRepository;

    @Mock
    private Model model;

    @InjectMocks
    private SpoolChangeGuiController controller;

    @Nested
    @DisplayName("selectTray")
    class SelectTray {

        @Test
        @DisplayName("returns spool-select view with spool and filament")
        void returnsViewWithSpool() {
            Vendor vendor = new Vendor();
            vendor.setId(1L);
            vendor.setName("Test Vendor");

            MaterialType materialType = new MaterialType();
            materialType.setId(1L);
            materialType.setName("PLA");

            Filament filament = new Filament();
            filament.setId(1L);
            filament.setFullId("Test Filament");
            filament.setVendor(vendor);
            filament.setMaterialType(materialType);
            filament.setColorHex("#FF0000");

            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            spool.setFilament(filament);

            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));

            String view = controller.selectTray(1L, model);

            assertThat(view).isEqualTo("s/spool-select");
            verify(model).addAttribute("spool", spool);
            verify(model).addAttribute("filament", filament);
        }

        @Test
        @DisplayName("throws 404 when spool not found")
        void throwsWhenNotFound() {
            when(filamentSpoolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.selectTray(99L, model))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }
}
