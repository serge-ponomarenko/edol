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
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.LabelService;
import org.spon.edolhub.service.spool.FilamentSpoolService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilamentSpoolControllerTest {

    @Mock
    private FilamentSpoolRepository filamentSpoolRepository;

    @Mock
    private FilamentSpoolService filamentSpoolService;

    @Mock
    private FilamentRepository filamentRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private MaterialTypeRepository materialRepository;

    @Mock
    private LabelService labelService;

    @Mock
    private Model model;

    @InjectMocks
    private FilamentSpoolController controller;

    @Nested
    @DisplayName("listSpools")
    class ListSpools {

        @Test
        @DisplayName("returns list view with filtered spools")
        void returnsListView() {
            Filament filament = new Filament();
            filament.setId(1L);
            FilamentSpool spool = new FilamentSpool();
            spool.setFilament(filament);

            when(filamentSpoolService.findFiltered(null, null,
                    List.of(FilamentSpool.FilamentSpoolStatus.SEALED, FilamentSpool.FilamentSpoolStatus.ACTIVE)))
                    .thenReturn(List.of(spool));
            when(vendorRepository.findAll()).thenReturn(List.of());
            when(materialRepository.findAll()).thenReturn(List.of());

            String view = controller.listSpools(null, null, null, model);

            assertThat(view).isEqualTo("dashboard/filament-spools/list");
            verify(model).addAttribute(eq("groupedSpools"), anyMap());
            verify(model).addAttribute("vendors", List.of());
            verify(model).addAttribute("materials", List.of());
        }

        @Test
        @DisplayName("uses provided status filter")
        void usesProvidedStatus() {
            when(filamentSpoolService.findFiltered(null, null,
                    List.of(FilamentSpool.FilamentSpoolStatus.ACTIVE)))
                    .thenReturn(List.of());
            when(vendorRepository.findAll()).thenReturn(List.of());
            when(materialRepository.findAll()).thenReturn(List.of());

            controller.listSpools(null, null, List.of(FilamentSpool.FilamentSpoolStatus.ACTIVE), model);

            verify(filamentSpoolService).findFiltered(null, null,
                    List.of(FilamentSpool.FilamentSpoolStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("createForm")
    class CreateForm {

        @Test
        @DisplayName("returns form view with new spool")
        void returnsFormView() {
            List<Filament> filaments = List.of(new Filament());
            when(filamentRepository.findAll()).thenReturn(filaments);

            String view = controller.createForm(model);

            assertThat(view).isEqualTo("dashboard/filament-spools/form");
            verify(model).addAttribute(eq("spool"), any(FilamentSpool.class));
            verify(model).addAttribute("filaments", filaments);
        }
    }

    @Nested
    @DisplayName("duplicate")
    class Duplicate {

        @Test
        @DisplayName("returns form view with duplicated spool")
        void returnsDuplicatedForm() {
            Vendor vendor = new Vendor();
            vendor.setId(1L);
            MaterialType materialType = new MaterialType();
            materialType.setId(1L);

            Filament filament = new Filament();
            filament.setId(1L);
            filament.setVendor(vendor);
            filament.setMaterialType(materialType);

            FilamentSpool source = new FilamentSpool();
            source.setId(5L);
            source.setFilament(filament);
            source.setWeightTotal(1000.0);
            source.setWeightRemaining(300.0);
            source.setPrice(java.math.BigDecimal.valueOf(25.00));
            source.setStatus(FilamentSpool.FilamentSpoolStatus.ACTIVE);
            source.setPurchasedAt(LocalDateTime.now());

            when(filamentSpoolRepository.findById(5L)).thenReturn(Optional.of(source));
            when(filamentRepository.findAll()).thenReturn(List.of());

            String view = controller.duplicate(5L, model);

            assertThat(view).isEqualTo("dashboard/filament-spools/form");
            verify(model).addAttribute(eq("spool"), any(FilamentSpool.class));
            verify(model).addAttribute("filaments", List.of());
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("saves single new spool")
        void savesSingle() {
            FilamentSpool spool = new FilamentSpool();
            spool.setWeightTotal(1000.0);

            String view = controller.save(spool, 1);

            assertThat(view).isEqualTo("redirect:/filament-spools");
            verify(filamentSpoolRepository).save(any(FilamentSpool.class));
        }

        @Test
        @DisplayName("saves multiple copies when quantity > 1")
        void savesMultiple() {
            FilamentSpool spool = new FilamentSpool();
            spool.setWeightTotal(1000.0);

            String view = controller.save(spool, 3);

            assertThat(view).isEqualTo("redirect:/filament-spools");
            verify(filamentSpoolRepository, times(3)).save(any(FilamentSpool.class));
        }

        @Test
        @DisplayName("sets weightRemaining from weightTotal when null")
        void setsWeightRemaining() {
            FilamentSpool spool = new FilamentSpool();
            spool.setWeightTotal(1000.0);
            spool.setWeightRemaining(null);

            controller.save(spool, 1);

            assertThat(spool.getWeightRemaining()).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("updates existing spool without duplication")
        void updatesExisting() {
            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            spool.setWeightTotal(1000.0);

            String view = controller.save(spool, 1);

            assertThat(view).isEqualTo("redirect:/filament-spools");
            verify(filamentSpoolRepository, times(1)).save(spool);
        }
    }

    @Nested
    @DisplayName("edit")
    class Edit {

        @Test
        @DisplayName("returns form view with existing spool")
        void returnsForm() {
            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));
            when(filamentRepository.findAll()).thenReturn(List.of());

            String view = controller.edit(1L, model);

            assertThat(view).isEqualTo("dashboard/filament-spools/form");
            verify(model).addAttribute("spool", spool);
            verify(model).addAttribute("filaments", List.of());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes spool and redirects")
        void deletesAndRedirects() {
            String view = controller.delete(1L);

            assertThat(view).isEqualTo("redirect:/filament-spools");
            verify(filamentSpoolRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("dry")
    class Dry {

        @Test
        @DisplayName("marks spool as dried")
        void marksDried() {
            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));

            ResponseEntity<?> response = controller.dry(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(spool.getLastDriedAt()).isNotNull();
            verify(filamentSpoolRepository).save(spool);
        }

        @Test
        @DisplayName("throws 404 when spool not found")
        void throwsWhenNotFound() {
            when(filamentSpoolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.dry(99L))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("generateLabel")
    class GenerateLabel {

        @Test
        @DisplayName("generates and returns label image")
        void returnsLabel() throws Exception {
            FilamentSpool spool = new FilamentSpool();
            spool.setId(1L);
            byte[] labelData = "label".getBytes();

            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));
            when(labelService.generateLabel(spool)).thenReturn(labelData);

            ResponseEntity<byte[]> response = controller.generateLabel(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("label.png");
            assertThat(response.getBody()).isEqualTo(labelData);
        }

        @Test
        @DisplayName("throws 404 when spool not found")
        void throwsWhenNotFound() {
            when(filamentSpoolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.generateLabel(99L))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
}
