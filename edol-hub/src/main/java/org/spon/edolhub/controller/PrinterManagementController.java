package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.dto.CorePrinterConnectionDto;
import org.spon.edolhub.model.dto.PrinterForm;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edolhub.service.PrinterManagementService;
import org.spon.edolhub.service.AmsTerminalManagementClient;
import org.spon.edolhub.service.AmsTerminalManagementConflictException;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.UUID;

@Controller
@RequestMapping("/printers")
@RequiredArgsConstructor
public class PrinterManagementController {

    private static final String PRINTER_ERROR_ATTRIBUTE = "printerError";
    private static final String TERMINAL_ERROR_ATTRIBUTE = "terminalError";
    private static final String TERMINAL_MANAGEMENT_AVAILABLE_ATTRIBUTE = "terminalManagementAvailable";
    private static final String PRINTER_EDIT_REDIRECT = "redirect:/printers/";

    private final PrinterAccessService printerAccessService;
    private final PrinterManagementService printerManagementService;
    private final PrinterCatalogSyncService printerCatalogSyncService;
    private final TenantOwnerAuthorizationService ownerAuthorizationService;
    private final ObjectProvider<AmsTerminalManagementClient> terminalManagementClientProvider;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("printers", printerAccessService.getPrinters());
        return "dashboard/printers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("printerForm", new PrinterForm());
        model.addAttribute("newPrinterForm", true);
        model.addAttribute("models", new String[]{"BAMBU_A1", "BAMBU_A1_MINI", "BAMBU_P1P", "BAMBU_P1S", "BAMBU_X1", "BAMBU_X1C", "BAMBU_H2D", "UNKNOWN"});
        model.addAttribute("connectionModes", new String[]{"DIRECT", "AGENT"});
        model.addAttribute("cameraProviders", new String[]{"LEGACY", "RTSPS"});
        return "dashboard/printers/form";
    }

    @PostMapping
    public String create(@ModelAttribute PrinterForm printerForm, RedirectAttributes redirectAttributes) {
        try {
            CorePrinterDto printer = printerManagementService.createPrinter(printerForm);
            printerCatalogSyncService.synchronize(printer.printerId());
            return PRINTER_EDIT_REDIRECT + printer.printerId();
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(PRINTER_ERROR_ATTRIBUTE, "Unable to create printer in EDOL Core");
            return "redirect:/printers/new";
        }
    }

    @GetMapping("/{printerId}/edit")
    public String editForm(@PathVariable UUID printerId, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        printerAccessService.getPrinter(printerId);
        CorePrinterDto printer = printerManagementService.getPrinter(printerId);
        CorePrinterConnectionDto connection;
        try {
            connection = printerManagementService.getConnection(printerId);
        } catch (HttpClientErrorException exception) {
            if (!exception.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.NOT_FOUND)) {
                throw exception;
            }
            connection = new CorePrinterConnectionDto(null, null, null, null, null, null);
            model.addAttribute(PRINTER_ERROR_ATTRIBUTE,
                    "Connection configuration is missing. Complete and save the form to recreate it.");
        }
        model.addAttribute(
                "printerForm",
                PrinterForm.from(printer, connection)
        );
        model.addAttribute("newPrinterForm", false);
        model.addAttribute("editingPrinterId", printerId);
        model.addAttribute("models", new String[]{"BAMBU_A1", "BAMBU_A1_MINI", "BAMBU_P1P", "BAMBU_P1S", "BAMBU_X1", "BAMBU_X1C", "BAMBU_H2D", "UNKNOWN"});
        model.addAttribute("connectionModes", new String[]{"DIRECT", "AGENT"});
        model.addAttribute("cameraProviders", new String[]{"LEGACY", "RTSPS"});
        populateTerminalManagement(printerId, model);
        return "dashboard/printers/form";
    }

    @PostMapping("/{printerId}/terminal/pairings")
    public String createTerminalPairing(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        return pairing(printerId, redirectAttributes, AmsTerminalManagementClient::createPairing);
    }

    @PostMapping("/{printerId}/terminal/rotate")
    public String rotateTerminalCredential(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        return pairing(printerId, redirectAttributes, AmsTerminalManagementClient::rotate);
    }

    @PostMapping("/{printerId}/terminal/replace")
    public String replaceTerminal(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        return pairing(printerId, redirectAttributes, AmsTerminalManagementClient::replace);
    }

    @PostMapping("/{printerId}/terminal/revoke")
    public String revokeTerminal(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        return command(printerId, redirectAttributes, AmsTerminalManagementClient::revoke,
                "Terminal enrollment has been revoked. The device must be paired again.");
    }

    @PostMapping("/{printerId}/terminal/factory-reset")
    public String factoryResetTerminal(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        return command(printerId, redirectAttributes, AmsTerminalManagementClient::factoryReset,
                "Terminal credentials have been invalidated. Clear the device locally before pairing again.");
    }

    @PostMapping("/{printerId}")
    public String update(@PathVariable UUID printerId,
                         @ModelAttribute PrinterForm printerForm,
                         RedirectAttributes redirectAttributes) {
        printerAccessService.getPrinter(printerId);
        try {
            printerManagementService.updatePrinter(printerId, printerForm);
            printerCatalogSyncService.synchronize(printerId);
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(PRINTER_ERROR_ATTRIBUTE, "Unable to update printer in EDOL Core");
        }
        return PRINTER_EDIT_REDIRECT + printerId + "/edit";
    }

    @PostMapping("/{printerId}/delete")
    public String delete(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        printerAccessService.getPrinter(printerId);
        try {
            printerManagementService.deletePrinter(printerId);
            printerCatalogSyncService.synchronize();
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(PRINTER_ERROR_ATTRIBUTE, "Unable to decommission printer in EDOL Core");
        }
        return "redirect:/printers";
    }

    private void populateTerminalManagement(UUID printerId, Model model) {
        AmsTerminalManagementClient client = terminalManagementClientProvider.getIfAvailable();
        if (client == null) {
            model.addAttribute(TERMINAL_MANAGEMENT_AVAILABLE_ATTRIBUTE, false);
            return;
        }
        try {
            ownerAuthorizationService.requireCurrentOwner();
            model.addAttribute(TERMINAL_MANAGEMENT_AVAILABLE_ATTRIBUTE, true);
            client.status(printerId).ifPresent(status -> {
                model.addAttribute("terminalStatus", status);
                model.addAttribute("terminalPairingExpired", "EXPIRED".equals(status.pairingState())
                        || ("PENDING".equals(status.pairingState())
                        && status.pairingExpiresAt() != null
                        && !status.pairingExpiresAt().isAfter(Instant.now())));
            });
        } catch (AccessDeniedException exception) {
            model.addAttribute(TERMINAL_MANAGEMENT_AVAILABLE_ATTRIBUTE, false);
        } catch (RuntimeException exception) {
            model.addAttribute(TERMINAL_MANAGEMENT_AVAILABLE_ATTRIBUTE, true);
            model.addAttribute(TERMINAL_ERROR_ATTRIBUTE, "AMS Terminal status is temporarily unavailable. Try again shortly.");
        }
    }

    private String pairing(
            UUID printerId,
            RedirectAttributes redirectAttributes,
            java.util.function.BiFunction<AmsTerminalManagementClient, UUID, AmsTerminalManagementClient.Pairing> operation
    ) {
        AmsTerminalManagementClient client = authorizeTerminalManagement(printerId);
        try {
            redirectAttributes.addFlashAttribute("terminalPairing", operation.apply(client, printerId));
        } catch (AmsTerminalManagementConflictException exception) {
            redirectAttributes.addFlashAttribute(TERMINAL_ERROR_ATTRIBUTE, exception.getMessage());
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(TERMINAL_ERROR_ATTRIBUTE, "AMS Terminal operation could not be completed.");
        }
        return terminalRedirect(printerId);
    }

    private String command(
            UUID printerId,
            RedirectAttributes redirectAttributes,
            java.util.function.BiConsumer<AmsTerminalManagementClient, UUID> operation,
            String successMessage
    ) {
        AmsTerminalManagementClient client = authorizeTerminalManagement(printerId);
        try {
            operation.accept(client, printerId);
            redirectAttributes.addFlashAttribute("terminalInfo", successMessage);
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(TERMINAL_ERROR_ATTRIBUTE, "AMS Terminal operation could not be completed.");
        }
        return terminalRedirect(printerId);
    }

    private AmsTerminalManagementClient authorizeTerminalManagement(UUID printerId) {
        ownerAuthorizationService.requireCurrentOwner();
        printerAccessService.getPrinter(printerId);
        AmsTerminalManagementClient client = terminalManagementClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("AMS Terminal management is unavailable outside secure multi-tenant mode");
        }
        return client;
    }

    private String terminalRedirect(UUID printerId) {
        return PRINTER_EDIT_REDIRECT + printerId + "/edit";
    }
}
