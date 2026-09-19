package org.spon.edolhub.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.spon.edolhub.repository.PrintJobRepository;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.spool.AllocationMutationService;
import org.spon.edolhub.service.spool.PrintAllocationPreviewMapper;
import org.spon.edolhub.service.spool.PrintAllocationReconciliationService;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintAllocationControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Mock private PrintAllocationPreviewRepository previewRepository;
    @Mock private PrintAllocationPreviewMapper previewMapper;
    @Mock private AllocationMutationService allocationMutationService;
    @Mock private PrintAllocationReconciliationService reconciliationService;
    @Mock private FilamentRepository filamentRepository;
    @Mock private FilamentSpoolRepository filamentSpoolRepository;
    @Mock private PrintJobRepository printJobRepository;
    @Mock private PrinterAccessService printerAccessService;
    @Mock private TenantContext tenantContext;
    @Mock private Model model;

    @InjectMocks
    private PrintAllocationController controller;

    private PrintJob job;

    @BeforeEach
    void setUp() {
        Printer printer = new Printer();
        printer.setId(PRINTER_ID);
        job = PrintJob.builder().printer(printer).build();
        job.setId(JOB_ID);
        org.mockito.Mockito.lenient().when(tenantContext.getCurrentTenantId()).thenReturn(TENANT_ID);
    }

    @Test
    void allocationPageRequiresJobFromSelectedPrinter() {
        when(printJobRepository.findByPublicIdAndPrinterTenantId(42L, TENANT_ID))
                .thenReturn(Optional.of(job));

        String view = controller.allocationPage(PRINTER_ID, 42L, model);

        assertThat(view).isEqualTo("dashboard/print-jobs/allocation");
        verify(model).addAttribute("printerId", PRINTER_ID);
    }

    @Test
    void getAllocationMapsTenantScopedPreview() {
        PrintAllocationPreview preview = new PrintAllocationPreview();
        PrintAllocationPreviewDto dto = new PrintAllocationPreviewDto();
        when(printJobRepository.findByPublicIdAndPrinterTenantId(42L, TENANT_ID))
                .thenReturn(Optional.of(job));
        when(previewRepository.findByPrintJobId(JOB_ID)).thenReturn(Optional.of(preview));
        when(previewMapper.toDto(preview)).thenReturn(dto);

        assertThat(controller.getAllocation(PRINTER_ID, 42L)).isSameAs(dto);
    }

    @Test
    void rerunUsesInternalUuidOnlyAfterScopeValidation() {
        when(printJobRepository.findByPublicIdAndPrinterTenantId(42L, TENANT_ID))
                .thenReturn(Optional.of(job));

        controller.rerunAllocation(PRINTER_ID, 42L, 7L);

        verify(allocationMutationService).rerunAllocation(JOB_ID, 7L);
    }

    @Test
    void rejectsJobOwnedByAnotherPrinter() {
        Printer otherPrinter = new Printer();
        otherPrinter.setId(OTHER_PRINTER_ID);
        job.setPrinter(otherPrinter);
        when(printJobRepository.findByPublicIdAndPrinterTenantId(42L, TENANT_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> controller.finalizeReconciliation(PRINTER_ID, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected printer");
    }

    @Test
    void filamentsAreTenantScoped() {
        Filament filament = new Filament();
        filament.setFullId("PLA Black");
        when(filamentRepository.findAllByTenantIdOrderByFullId(TENANT_ID))
                .thenReturn(List.of(filament));

        assertThat(controller.filaments(PRINTER_ID, "black")).containsExactly(filament);
    }
}
