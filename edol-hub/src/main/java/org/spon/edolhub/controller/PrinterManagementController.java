package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.dto.PrinterForm;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edolhub.service.PrinterManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/printers")
@RequiredArgsConstructor
public class PrinterManagementController {

    private final PrinterAccessService printerAccessService;
    private final PrinterManagementService printerManagementService;
    private final PrinterCatalogSyncService printerCatalogSyncService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("printers", printerAccessService.getPrinters());
        return "dashboard/printers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("printerForm", new PrinterForm());
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
            return "redirect:/printers/" + printer.printerId();
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("printerError", "Unable to create printer in EDOL Core");
            return "redirect:/printers/new";
        }
    }

    @GetMapping("/{printerId}/edit")
    public String editForm(@PathVariable UUID printerId, Model model) {
        printerAccessService.getPrinter(printerId);
        model.addAttribute(
                "printerForm",
                PrinterForm.from(
                        printerManagementService.getPrinter(printerId),
                        printerManagementService.getConnection(printerId)
                )
        );
        model.addAttribute("printerId", printerId);
        model.addAttribute("models", new String[]{"BAMBU_A1", "BAMBU_A1_MINI", "BAMBU_P1P", "BAMBU_P1S", "BAMBU_X1", "BAMBU_X1C", "BAMBU_H2D", "UNKNOWN"});
        model.addAttribute("connectionModes", new String[]{"DIRECT", "AGENT"});
        model.addAttribute("cameraProviders", new String[]{"LEGACY", "RTSPS"});
        return "dashboard/printers/form";
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
            redirectAttributes.addFlashAttribute("printerError", "Unable to update printer in EDOL Core");
        }
        return "redirect:/printers/" + printerId + "/edit";
    }

    @PostMapping("/{printerId}/delete")
    public String delete(@PathVariable UUID printerId, RedirectAttributes redirectAttributes) {
        printerAccessService.getPrinter(printerId);
        try {
            printerManagementService.deletePrinter(printerId);
            printerCatalogSyncService.synchronize();
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("printerError", "Unable to decommission printer in EDOL Core");
        }
        return "redirect:/printers";
    }
}
