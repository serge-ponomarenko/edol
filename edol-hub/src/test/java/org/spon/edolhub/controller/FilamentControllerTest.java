package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.FilamentDeletePreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.filament.FilamentDeleteService;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilamentControllerTest {

    @Mock
    private FilamentRepository filamentRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private MaterialTypeRepository materialTypeRepository;

    @Mock
    private FilamentDeleteService filamentDeleteService;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private FilamentController controller;

    @Nested
    @DisplayName("list")
    class ListFilaments {

        @Test
        @DisplayName("returns list view with all filaments")
        void returnsListView() {
            List<Filament> filaments = List.of(new Filament());
            when(filamentRepository.findAll()).thenReturn(filaments);

            String view = controller.list(model);

            assertThat(view).isEqualTo("dashboard/filaments/list");
            verify(model).addAttribute("filaments", filaments);
        }
    }

    @Nested
    @DisplayName("createForm")
    class CreateForm {

        @Test
        @DisplayName("returns form view with data")
        void returnsFormView() {
            List<Vendor> vendors = List.of(new Vendor());
            List<MaterialType> materials = List.of(new MaterialType());
            when(vendorRepository.findAll()).thenReturn(vendors);
            when(materialTypeRepository.findAll()).thenReturn(materials);

            String view = controller.createForm(model);

            assertThat(view).isEqualTo("dashboard/filaments/form");
            verify(model).addAttribute(eq("filament"), any(Filament.class));
            verify(model).addAttribute("vendors", vendors);
            verify(model).addAttribute("materialTypes", materials);
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("saves filament with uppercased hex and redirects")
        void savesAndRedirects() {
            Filament filament = new Filament();
            filament.setColorHex("#ff0000");

            String view = controller.save(filament);

            assertThat(view).isEqualTo("redirect:/filaments");
            assertThat(filament.getColorHex()).isEqualTo("#FF0000");
            verify(filamentRepository).save(filament);
        }
    }

    @Nested
    @DisplayName("edit")
    class Edit {

        @Test
        @DisplayName("returns form view with existing filament")
        void returnsFormWithFilament() {
            Filament filament = new Filament();
            filament.setId(1L);
            when(filamentRepository.findById(1L)).thenReturn(Optional.of(filament));
            when(vendorRepository.findAll()).thenReturn(List.of());
            when(materialTypeRepository.findAll()).thenReturn(List.of());

            String view = controller.edit(1L, model);

            assertThat(view).isEqualTo("dashboard/filaments/form");
            verify(model).addAttribute("filament", filament);
            verify(model).addAttribute("vendors", List.of());
            verify(model).addAttribute("materialTypes", List.of());
        }

        @Test
        @DisplayName("throws when filament not found")
        void throwsWhenNotFound() {
            when(filamentRepository.findById(99L)).thenReturn(Optional.empty());

            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.edit(99L, model)
            );
        }
    }

    @Nested
    @DisplayName("previewDelete")
    class PreviewDelete {

        @Test
        @DisplayName("returns delete preview")
        void returnsPreview() {
            FilamentDeletePreviewDto preview = new FilamentDeletePreviewDto(List.of(), List.of());
            when(filamentDeleteService.preview(1L)).thenReturn(preview);

            FilamentDeletePreviewDto result = controller.previewDelete(1L);

            assertThat(result).isSameAs(preview);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes filament when no dependencies")
        void deletesWhenNoDeps() {
            FilamentDeletePreviewDto preview = new FilamentDeletePreviewDto(List.of(), List.of());
            when(filamentDeleteService.preview(1L)).thenReturn(preview);

            String view = controller.delete(1L, redirectAttributes);

            assertThat(view).isEqualTo("redirect:/filaments");
            verify(filamentRepository).deleteById(1L);
            verifyNoInteractions(redirectAttributes);
        }

        @Test
        @DisplayName("does not delete when dependencies exist")
        void doesNotDeleteWhenDepsExist() {
            FilamentDeletePreviewDto preview = new FilamentDeletePreviewDto(
                    List.of(new FilamentDeletePreviewDto.SpoolInfo(1L, 500.0, "ACTIVE")),
                    List.of()
            );
            when(filamentDeleteService.preview(1L)).thenReturn(preview);

            String view = controller.delete(1L, redirectAttributes);

            assertThat(view).isEqualTo("redirect:/filaments");
            verify(filamentRepository, never()).deleteById(any());
            verify(redirectAttributes).addFlashAttribute("deleteError", "Filament is used by other entities");
        }
    }
}
