package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.service.PrinterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final PrinterService printerService;

    @GetMapping("/")
    public String dashboard(Model model) {
        PrinterState state = printerService.getState();

        model.addAttribute("printer", state);

        return "dashboard/index";
    }

}
