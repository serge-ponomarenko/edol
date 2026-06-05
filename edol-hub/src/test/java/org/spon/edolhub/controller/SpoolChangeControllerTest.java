package org.spon.edolhub.controller;

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
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpoolChangeControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FilamentSpoolRepository filamentSpoolRepository;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private SpoolChangeController controller;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private FilamentSpool spool;
    private Filament filament;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "edolCoreUrl", "http://edolcore:8080");

        Vendor vendor = new Vendor();
        vendor.setId(1L);
        vendor.setName("Test Vendor");

        MaterialType materialType = new MaterialType();
        materialType.setId(1L);
        materialType.setName("PLA");

        filament = new Filament();
        filament.setId(1L);
        filament.setFullId("Test Filament");
        filament.setVendor(vendor);
        filament.setMaterialType(materialType);
        filament.setColorHex("#FF0000");
        filament.setBrand("Matte");
        filament.setPrinterFilamentProfileId("P001");

        spool = new FilamentSpool();
        spool.setId(1L);
        spool.setFilament(filament);
    }

    @Nested
    @DisplayName("spoolChange")
    class SpoolChange {

        @Test
        @DisplayName("sends spool change request and redirects")
        void sendsRequestAndRedirects() {
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/spool-change"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("done"));

            String view = controller.spoolChange(1L, 0, redirectAttributes);

            assertThat(view).isEqualTo("redirect:/s/1");
            verify(restTemplate).exchange(
                    eq("http://edolcore:8080/printer/request/spool-change"),
                    eq(HttpMethod.POST),
                    requestCaptor.capture(),
                    eq(String.class)
            );

            Map<String, Object> body = requestCaptor.getValue().getBody();
            assertThat(body)
                    .isNotNull()
                    .containsEntry("amsId", 0)
                    .containsEntry("trayId", 0)
                    .containsEntry("trayInfoIdx", "P001")
                    .containsEntry("trayColor", "FF0000FF")
                    .containsEntry("trayType", "PLA");

            verify(redirectAttributes).addFlashAttribute("toastMessage", "Matte → Tray 1");
            verify(redirectAttributes).addFlashAttribute("toastColor", "#FF0000");
        }

        @Test
        @DisplayName("uses amsId 254 for trayId 254")
        void usesAmsId254() {
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));
            when(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).thenReturn(ResponseEntity.ok("done"));

            controller.spoolChange(1L, 254, redirectAttributes);

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class)
            );
            assertThat(requestCaptor.getValue().getBody())
                    .isNotNull()
                    .containsEntry("amsId", 254);
        }

        @Test
        @DisplayName("uses amsId 255 for trayId 255")
        void usesAmsId255() {
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));
            when(restTemplate.exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).thenReturn(ResponseEntity.ok("done"));

            controller.spoolChange(1L, 255, redirectAttributes);

            verify(restTemplate).exchange(
                    anyString(), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class)
            );
            assertThat(requestCaptor.getValue().getBody())
                    .isNotNull()
                    .containsEntry("amsId", 255);
        }

        @Test
        @DisplayName("throws 400 for trayId -1")
        void throwsForNegativeTrayId() {
            assertThatThrownBy(() -> controller.spoolChange(1L, -1, redirectAttributes))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws 400 for trayId 4")
        void throwsForInvalidTrayId() {
            assertThatThrownBy(() -> controller.spoolChange(1L, 4, redirectAttributes))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws 404 when spool not found")
        void throwsWhenSpoolNotFound() {
            when(filamentSpoolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.spoolChange(99L, 0, redirectAttributes))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("throws 400 for invalid color hex")
        void throwsForInvalidColor() {
            filament.setColorHex("invalid");
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.spoolChange(1L, 0, redirectAttributes))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("throws 400 for null color")
        void throwsForNullColor() {
            filament.setColorHex(null);
            when(filamentSpoolRepository.findById(1L)).thenReturn(Optional.of(spool));

            assertThatThrownBy(() -> controller.spoolChange(1L, 0, redirectAttributes))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        }
    }
}
