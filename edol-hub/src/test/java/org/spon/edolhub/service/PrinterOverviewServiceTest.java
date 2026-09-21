package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.controller.PrinterStateController.PrinterStateEnriched;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.dto.MaintenanceStatusDto;
import org.spon.edolhub.model.dto.PrinterOverviewDto;
import org.spon.edolhub.model.entity.Printer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterOverviewServiceTest {

    private static final UUID FIRST_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private PrinterService printerService;

    @Mock
    private PrinterDashboardStateService printerDashboardStateService;

    @Mock
    private MaintenanceService maintenanceService;

    @InjectMocks
    private PrinterOverviewService service;

    @Test
    void returnsLiveStateAndModelOnlyForPrintersPresentInTheCurrentCoreCatalog() {
        Printer first = printer(FIRST_PRINTER_ID, "P1S", true);
        Printer second = printer(SECOND_PRINTER_ID, "X1C", true);
        PrinterState state = new PrinterState();
        state.setOnline(true);
        PrinterStateEnriched enriched = new PrinterStateEnriched();
        enriched.setPrinterState(state);
        enriched.setJobId(17L);
        MaintenanceStatusDto due = new MaintenanceStatusDto();
        due.setDue(true);

        when(printerAccessService.getPrinters()).thenReturn(List.of(first, second));
        when(printerService.getPrinters()).thenReturn(List.of(
                new CorePrinterDto(FIRST_PRINTER_ID, "P1S", "Garage P1S", null, null, "BAMBU_P1S", null, null, true)
        ));
        when(printerDashboardStateService.getState(FIRST_PRINTER_ID)).thenReturn(enriched);
        when(maintenanceService.getMaintenanceStatus(FIRST_PRINTER_ID)).thenReturn(List.of(due));
        when(maintenanceService.getMaintenanceStatus(SECOND_PRINTER_ID)).thenReturn(List.of());

        List<PrinterOverviewDto> overview = service.getOverview();

        assertThat(overview).containsExactly(
                new PrinterOverviewDto(
                        FIRST_PRINTER_ID,
                        "P1S",
                        "P1S",
                        "BAMBU_P1S",
                        true,
                        true,
                        state,
                        17L,
                        1
                ),
                new PrinterOverviewDto(
                        SECOND_PRINTER_ID,
                        "X1C",
                        "X1C",
                        null,
                        true,
                        false,
                        null,
                        null,
                        0
                )
        );
        verify(printerDashboardStateService, never()).getState(SECOND_PRINTER_ID);
    }

    @Test
    void isolatesCoreAndMaintenanceFailuresToTheAffectedPrinter() {
        Printer first = printer(FIRST_PRINTER_ID, "P1S", true);
        Printer second = printer(SECOND_PRINTER_ID, "X1C", true);
        PrinterState state = new PrinterState();
        state.setOnline(true);
        PrinterStateEnriched enriched = new PrinterStateEnriched();
        enriched.setPrinterState(state);

        when(printerAccessService.getPrinters()).thenReturn(List.of(first, second));
        when(printerService.getPrinters()).thenReturn(List.of(
                new CorePrinterDto(FIRST_PRINTER_ID, "P1S", "Garage P1S", null, null, "BAMBU_P1S", null, null, true),
                new CorePrinterDto(SECOND_PRINTER_ID, "X1C", "Office X1C", null, null, "BAMBU_X1C", null, null, true)
        ));
        doThrow(new IllegalStateException("Core state failed"))
                .when(printerDashboardStateService).getState(FIRST_PRINTER_ID);
        when(printerDashboardStateService.getState(SECOND_PRINTER_ID)).thenReturn(enriched);
        doThrow(new IllegalStateException("Maintenance failed"))
                .when(maintenanceService).getMaintenanceStatus(FIRST_PRINTER_ID);
        when(maintenanceService.getMaintenanceStatus(SECOND_PRINTER_ID)).thenReturn(List.of());

        List<PrinterOverviewDto> overview = service.getOverview();

        assertThat(overview).hasSize(2);
        assertThat(overview.get(0).state()).isNull();
        assertThat(overview.get(0).maintenanceAlertCount()).isZero();
        assertThat(overview.get(1).state()).isSameAs(state);
        verify(printerDashboardStateService).getState(SECOND_PRINTER_ID);
    }

    private Printer printer(UUID id, String displayId, boolean availableInCore) {
        Printer printer = new Printer();
        printer.setId(id);
        printer.setDisplayId(displayId);
        printer.setName(displayId);
        printer.setEnabled(true);
        printer.setAvailableInCore(availableInCore);
        return printer;
    }
}
