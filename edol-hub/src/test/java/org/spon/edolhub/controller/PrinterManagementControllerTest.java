package org.spon.edolhub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.PrinterForm;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edolhub.service.PrinterManagementService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PrinterManagementControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private PrinterManagementService printerManagementService;

    @Mock
    private PrinterCatalogSyncService printerCatalogSyncService;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private PrinterManagementController controller;

    @Test
    void scopesUpdateToTheCurrentTenantAndSynchronizesProjection() {
        PrinterForm form = new PrinterForm();
        String view = controller.update(PRINTER_ID, form, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/printers/" + PRINTER_ID + "/edit");
        verify(printerAccessService).getPrinter(PRINTER_ID);
        verify(printerManagementService).updatePrinter(PRINTER_ID, form);
        verify(printerCatalogSyncService).synchronize(PRINTER_ID);
    }
}
