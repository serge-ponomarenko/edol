package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantContext;
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
    private final TenantContext tenantContext;
    private final PrinterAccessService printerAccessService;

    @GetMapping("/{spoolId}")
    public String selectTray(@PathVariable Long spoolId, Model model) {
        FilamentSpool spool = filamentSpoolRepository.findByIdAndFilamentTenantId(
                        spoolId,
                        tenantContext.getCurrentTenantId()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var filament = spool.getFilament();

        model.addAttribute("spool", spool);
        model.addAttribute("filament", filament);
        model.addAttribute("printers", printerAccessService.getPrinters());

        return "s/spool-select";
    }
}
