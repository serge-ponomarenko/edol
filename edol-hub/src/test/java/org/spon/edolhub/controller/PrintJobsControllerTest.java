package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.service.TenantContext;
import org.springframework.ui.Model;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintJobsControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private Model model;

    @InjectMocks
    private PrintJobsController controller;

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("returns list view")
        void returnsListView() {
            String view = controller.list(PRINTER_ID, model);
            assertThat(view).isEqualTo("dashboard/print-jobs/list");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes job and redirects")
        void deletesAndRedirects() {
            Printer printer = new Printer();
            printer.setId(PRINTER_ID);
            PrintJob job = PrintJob.builder().printer(printer).build();
            when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
            when(printJobRepository.findByPublicIdAndPrinterTenantId(1L, TENANT_ID)).thenReturn(Optional.of(job));

            String view = controller.delete(PRINTER_ID, 1L);
            assertThat(view).isEqualTo("redirect:/printers/" + PRINTER_ID + "/print-jobs");
            verify(printJobRepository).delete(job);
        }
    }
}
