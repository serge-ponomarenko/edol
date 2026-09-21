package org.spon.edolhub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.PrinterOverviewDto;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterCatalogStatus;
import org.spon.edolhub.service.PrinterOverviewService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterCatalogControllerTest {

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private PrinterCatalogStatus status;

    @Mock
    private PrinterOverviewService printerOverviewService;

    @InjectMocks
    private PrinterCatalogController controller;

    @Test
    void returnsTheTenantScopedPrinterOverview() {
        PrinterOverviewDto printer = new PrinterOverviewDto(null, "P1S", "Garage P1S", "BAMBU_P1S", true, true, null, null, 0);
        when(printerOverviewService.getOverview()).thenReturn(List.of(printer));

        assertThat(controller.getOverview()).containsExactly(printer);
    }
}
