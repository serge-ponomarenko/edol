package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/s")
public class SpoolChangeGuiController {

    private final FilamentSpoolRepository filamentSpoolRepository;

    @GetMapping("/{spoolId}")
    public String selectTray(@PathVariable Long spoolId, Model model) {
        FilamentSpool spool = filamentSpoolRepository.findById(spoolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var filament = spool.getFilament();

        model.addAttribute("spool", spool);
        model.addAttribute("filament", filament);

        return "s/spool-select";
    }
}
