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
import org.spon.edolhub.service.AmsTerminalManagementClient;
import org.spon.edolhub.service.AmsTerminalManagementConflictException;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private TenantOwnerAuthorizationService ownerAuthorizationService;

    @Mock
    private ObjectProvider<AmsTerminalManagementClient> terminalManagementClientProvider;

    @Mock
    private AmsTerminalManagementClient terminalManagementClient;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private Model model;

    @Mock
    private HttpServletResponse response;

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

    @Test
    void marksTheNewPrinterFormIndependentlyOfTheSelectedPrinter() {
        String view = controller.createForm(model);

        assertThat(view).isEqualTo("dashboard/printers/form");
        verify(model).addAttribute(eq("printerForm"), any(PrinterForm.class));
        verify(model).addAttribute("newPrinterForm", true);
    }

    @Test
    void suppliesAnExplicitPrinterIdOnlyForTheEditForm() {
        org.spon.edolhub.model.entity.Printer printer = new org.spon.edolhub.model.entity.Printer();
        printer.setId(PRINTER_ID);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(printerManagementService.getPrinter(PRINTER_ID)).thenReturn(
                new org.spon.edolhub.model.dto.CorePrinterDto(PRINTER_ID, "P1", "Printer", true)
        );
        when(printerManagementService.getConnection(PRINTER_ID)).thenReturn(
                new org.spon.edolhub.model.dto.CorePrinterConnectionDto(null, null, null, null, null, null, null)
        );

        String view = controller.editForm(PRINTER_ID, model, response);

        assertThat(view).isEqualTo("dashboard/printers/form");
        verify(model).addAttribute("newPrinterForm", false);
        verify(model).addAttribute("editingPrinterId", PRINTER_ID);
    }

    @Test
    void opensTheEditFormWhenTheConnectionConfigurationIsMissing() {
        org.spon.edolhub.model.entity.Printer printer = new org.spon.edolhub.model.entity.Printer();
        printer.setId(PRINTER_ID);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(printerManagementService.getPrinter(PRINTER_ID)).thenReturn(
                new org.spon.edolhub.model.dto.CorePrinterDto(PRINTER_ID, "P1", "Printer", true)
        );
        when(printerManagementService.getConnection(PRINTER_ID))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Not Found",
                        org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        String view = controller.editForm(PRINTER_ID, model, response);

        assertThat(view).isEqualTo("dashboard/printers/form");
        verify(model).addAttribute(eq("printerForm"), any(PrinterForm.class));
        verify(model).addAttribute(eq("printerError"), any(String.class));
    }

    @Test
    void createsTerminalPairingThroughTheOwnerScopedClient() {
        AmsTerminalManagementClient.Pairing pairing = new AmsTerminalManagementClient.Pairing(
                UUID.randomUUID(), PRINTER_ID, "ABCDEFGHIJK", java.time.Instant.parse("2026-10-10T12:00:00Z")
        );
        when(terminalManagementClientProvider.getIfAvailable()).thenReturn(terminalManagementClient);
        when(terminalManagementClient.createPairing(PRINTER_ID)).thenReturn(pairing);

        String view = controller.createTerminalPairing(PRINTER_ID, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/printers/" + PRINTER_ID + "/edit");
        verify(ownerAuthorizationService).requireCurrentOwner();
        verify(printerAccessService).getPrinter(PRINTER_ID);
        verify(redirectAttributes).addFlashAttribute("terminalPairing", pairing);
    }

    @Test
    void marksAnExpiredPairingAsRecoverableInTheEditForm() {
        org.spon.edolhub.model.entity.Printer printer = new org.spon.edolhub.model.entity.Printer();
        printer.setId(PRINTER_ID);
        AmsTerminalManagementClient.TerminalStatus status = new AmsTerminalManagementClient.TerminalStatus(
                UUID.randomUUID(), "PENDING", java.time.Instant.now().minusSeconds(600), null, null,
                null, null, null, "EXPIRED", java.time.Instant.now().minusSeconds(1)
        );
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(printerManagementService.getPrinter(PRINTER_ID)).thenReturn(
                new org.spon.edolhub.model.dto.CorePrinterDto(PRINTER_ID, "P1", "Printer", true)
        );
        when(printerManagementService.getConnection(PRINTER_ID)).thenReturn(
                new org.spon.edolhub.model.dto.CorePrinterConnectionDto(null, null, null, null, null, null, null)
        );
        when(terminalManagementClientProvider.getIfAvailable()).thenReturn(terminalManagementClient);
        when(terminalManagementClient.status(PRINTER_ID)).thenReturn(Optional.of(status));

        controller.editForm(PRINTER_ID, model, response);

        verify(model).addAttribute("terminalPairingExpired", true);
    }

    @Test
    void showsARecoveryMessageForAnExistingTerminalPairing() {
        when(terminalManagementClientProvider.getIfAvailable()).thenReturn(terminalManagementClient);
        when(terminalManagementClient.createPairing(PRINTER_ID))
                .thenThrow(new AmsTerminalManagementConflictException("already active"));

        controller.createTerminalPairing(PRINTER_ID, redirectAttributes);

        verify(redirectAttributes).addFlashAttribute(eq("terminalError"), any(String.class));
    }
}
