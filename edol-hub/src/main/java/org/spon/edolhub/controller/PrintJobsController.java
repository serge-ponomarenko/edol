package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.repository.PrintJobRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/print-jobs")
public class PrintJobsController {

    private final PrintJobRepository printJobRepository;

    @GetMapping
    public String list(Model model) {
        return "dashboard/print-jobs/list";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        printJobRepository.deleteById(id);
        return "redirect:/print-jobs";
    }

}
