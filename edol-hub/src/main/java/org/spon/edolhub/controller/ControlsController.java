package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/controls")
public class ControlsController {

    @GetMapping("/skip-objects")
    public String skipObjects(Model model) {
        return "dashboard/controls/skip-objects";
    }

}