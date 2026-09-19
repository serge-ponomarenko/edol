package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.PageResponse;
import org.spon.edolhub.model.dto.PrintJobDto;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.PrintJobMapper;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintJobControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private PrintJobService printJobService;

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PrintJobMapper printJobMapper;

    @Mock
    private TenantContext tenantContext;

    @InjectMocks
    private PrintJobController controller;

    @Nested
    @DisplayName("image")
    class Image {

        @Test
        @DisplayName("returns plate image when present")
        void returnsPlateImage() {
            byte[] imageData = "fake-image-data".getBytes();
            PrintJob job = PrintJob.builder().build();
            job.setPlateImage(imageData);
            job.setPlateImageType("image/png");

            when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
            when(printJobRepository.findByPublicIdAndPrinterTenantId(1L, TENANT_ID)).thenReturn(Optional.of(job));

            ResponseEntity<byte[]> response = controller.image(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/png");
            assertThat(response.getBody()).isEqualTo(imageData);
        }

        @Test
        @DisplayName("returns SVG placeholder when no plate image")
        void returnsSvgPlaceholder() {
            PrintJob job = PrintJob.builder().build();

            when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
            when(printJobRepository.findByPublicIdAndPrinterTenantId(1L, TENANT_ID)).thenReturn(Optional.of(job));

            ResponseEntity<byte[]> response = controller.image(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("image/svg+xml"));
            assertThat(response.getBody()).isNotNull();
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            assertThat(body).contains("No Preview");
        }

        @Test
        @DisplayName("throws when job not found")
        void throwsWhenNotFound() {
            when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
            when(printJobRepository.findByPublicIdAndPrinterTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.image(99L))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getJobs")
    class GetJobs {

        @Test
        @DisplayName("returns paged job DTOs")
        void returnsPagedJobs() {
            PrintJob job = PrintJob.builder().build();
            PrintJobDto dto = new PrintJobDto();
            dto.setId(1L);

            Page<PrintJob> jobPage = new PageImpl<>(List.of(job));
            when(printJobService.getJobs(PRINTER_ID, 0, 10)).thenReturn(jobPage);
            when(printJobMapper.toDto(job)).thenReturn(dto);

            PageResponse<PrintJobDto> result = controller.getJobs(PRINTER_ID, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getId()).isEqualTo(1L);
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(1);
        }
    }
}
