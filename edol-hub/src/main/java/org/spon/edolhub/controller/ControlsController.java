package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/printers/{printerId}/controls")
public class ControlsController {

    @GetMapping("/skip-objects")
    public String skipObjects(@PathVariable UUID printerId, Model model) {
        model.addAttribute("printerId", printerId);
        return "dashboard/controls/skip-objects";
    }

}
