package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialTypeControllerTest {

    @Mock
    private MaterialTypeRepository materialRepository;

    @Mock
    private Model model;

    @InjectMocks
    private MaterialTypeController controller;

    @Nested
    @DisplayName("list")
    class ListMaterialTypes {

        @Test
        @DisplayName("returns list view with all materials")
        void returnsListView() {
            List<MaterialType> materials = List.of(new MaterialType(), new MaterialType());
            when(materialRepository.findAll()).thenReturn(materials);

            String view = controller.list(model);

            assertThat(view).isEqualTo("dashboard/materials/list");
            verify(model).addAttribute("materials", materials);
        }
    }

    @Nested
    @DisplayName("createForm")
    class CreateForm {

        @Test
        @DisplayName("returns form view with new material")
        void returnsFormView() {
            String view = controller.createForm(model);

            assertThat(view).isEqualTo("dashboard/materials/form");
            verify(model).addAttribute(eq("material"), any(MaterialType.class));
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("saves material and redirects")
        void savesAndRedirects() {
            MaterialType material = new MaterialType();
            material.setName("PLA");

            String view = controller.save(material);

            assertThat(view).isEqualTo("redirect:/materials");
            verify(materialRepository).save(material);
        }
    }

    @Nested
    @DisplayName("edit")
    class Edit {

        @Test
        @DisplayName("returns form view with existing material")
        void returnsFormWithMaterial() {
            MaterialType material = new MaterialType();
            material.setId(1L);
            material.setName("PLA");
            when(materialRepository.findById(1L)).thenReturn(Optional.of(material));

            String view = controller.edit(1L, model);

            assertThat(view).isEqualTo("dashboard/materials/form");
            verify(model).addAttribute("material", material);
        }

        @Test
        @DisplayName("throws when material not found")
        void throwsWhenNotFound() {
            when(materialRepository.findById(99L)).thenReturn(Optional.empty());

            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.edit(99L, model)
            );
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes material and redirects")
        void deletesAndRedirects() {
            String view = controller.delete(1L);

            assertThat(view).isEqualTo("redirect:/materials");
            verify(materialRepository).deleteById(1L);
        }
    }
}
