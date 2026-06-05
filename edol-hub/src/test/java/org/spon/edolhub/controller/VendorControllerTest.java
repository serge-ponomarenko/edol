package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.VendorRepository;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorControllerTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private Model model;

    @InjectMocks
    private VendorController controller;

    @Nested
    @DisplayName("list")
    class ListVendors {

        @Test
        @DisplayName("returns list view with all vendors")
        void returnsListView() {
            List<Vendor> vendors = List.of(new Vendor(), new Vendor());
            when(vendorRepository.findAll()).thenReturn(vendors);

            String view = controller.list(model);

            assertThat(view).isEqualTo("dashboard/vendors/list");
            verify(model).addAttribute("vendors", vendors);
        }
    }

    @Nested
    @DisplayName("createForm")
    class CreateForm {

        @Test
        @DisplayName("returns form view with new vendor")
        void returnsFormView() {
            String view = controller.createForm(model);

            assertThat(view).isEqualTo("dashboard/vendors/form");
            verify(model).addAttribute(eq("vendor"), any(Vendor.class));
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("saves vendor and redirects")
        void savesAndRedirects() {
            Vendor vendor = new Vendor();
            vendor.setName("Test Vendor");

            String view = controller.save(vendor);

            assertThat(view).isEqualTo("redirect:/vendors");
            verify(vendorRepository).save(vendor);
        }
    }

    @Nested
    @DisplayName("edit")
    class Edit {

        @Test
        @DisplayName("returns form view with existing vendor")
        void returnsFormWithVendor() {
            Vendor vendor = new Vendor();
            vendor.setId(1L);
            vendor.setName("Test Vendor");
            when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor));

            String view = controller.edit(1L, model);

            assertThat(view).isEqualTo("dashboard/vendors/form");
            verify(model).addAttribute("vendor", vendor);
        }

        @Test
        @DisplayName("throws when vendor not found")
        void throwsWhenNotFound() {
            when(vendorRepository.findById(99L)).thenReturn(Optional.empty());

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
        @DisplayName("deletes vendor and redirects")
        void deletesAndRedirects() {
            String view = controller.delete(1L);

            assertThat(view).isEqualTo("redirect:/vendors");
            verify(vendorRepository).deleteById(1L);
        }
    }
}
