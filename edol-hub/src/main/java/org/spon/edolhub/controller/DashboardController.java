package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final PrinterService printerService;
    private final PrinterAccessService printerAccessService;

    @GetMapping("/")
    public String defaultDashboard() {
        return "redirect:/printers/" + printerAccessService.getDefaultPrinter().getId();
    }

    @GetMapping("/printers/{printerId}")
    public String dashboard(@PathVariable UUID printerId, Model model) {
        printerAccessService.getPrinter(printerId);
        PrinterState state = printerService.getState(printerId);

        boolean offline = state == null;

        if (state == null) {
            state = new PrinterState();
        }

        model.addAttribute(
                "printer",
                state
        );

        model.addAttribute(
                "printerOffline",
                offline
        );

        model.addAttribute("printerId", printerId);
        model.addAttribute("printers", printerAccessService.getPrinters());

        return "dashboard/index";
    }

}
