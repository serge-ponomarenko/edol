package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.spon.edoldashboard.repository.MaterialTypeRepository;
import org.spon.edoldashboard.repository.VendorRepository;
import org.spon.edoldashboard.service.FilamentSpoolService;
import org.spon.edoldashboard.service.LabelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/filament-spools")
public class FilamentSpoolController {

    private final FilamentSpoolRepository filamentSpoolRepository;
    private final FilamentSpoolService filamentSpoolService;
    private final FilamentRepository filamentRepository;
    private final VendorRepository vendorRepository;
    private final MaterialTypeRepository materialRepository;
    private final LabelService labelService;


    @GetMapping
    public String listSpools(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) List<FilamentSpool.FilamentSpoolStatus> status,
            Model model
    ) {
        // default: NEW + ACTIVE
        if (status == null || status.isEmpty()) {
            status = List.of(
                    FilamentSpool.FilamentSpoolStatus.NEW,
                    FilamentSpool.FilamentSpoolStatus.ACTIVE
            );
        }

        List<FilamentSpool> spools =
                filamentSpoolService.findFiltered(vendor, material, status);

        model.addAttribute("spools", spools);
        model.addAttribute("selectedVendor", vendor);
        model.addAttribute("selectedMaterial", material);
        model.addAttribute("selectedStatus", status);

        model.addAttribute("vendors", vendorRepository.findAll());
        model.addAttribute("materials", materialRepository.findAll());

        return "dashboard/filament-spools/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        FilamentSpool spool = new FilamentSpool();
        spool.setOpenedAt(LocalDateTime.now());

        model.addAttribute("spool", spool);
        model.addAttribute("filaments", filamentRepository.findAll());
        return "dashboard/filament-spools/form";
    }

    @PostMapping
    public String save(@ModelAttribute FilamentSpool spool) {
        if (spool.getWeightRemaining() == null) {
            spool.setWeightRemaining(spool.getWeightTotal());
        }

        if (spool.getOpenedAt() == null) {
            spool.setOpenedAt(LocalDateTime.now());
        }

        filamentSpoolRepository.save(spool);

        return "redirect:/filament-spools";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        FilamentSpool spool = filamentSpoolRepository.findById(id).orElseThrow();

        model.addAttribute("spool", spool);
        model.addAttribute("filaments", filamentRepository.findAll());

        return "dashboard/filament-spools/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        filamentSpoolRepository.deleteById(id);

        return "redirect:/filament-spools";
    }

    @PostMapping("/dry/{id}")
    public ResponseEntity<?> dry(@PathVariable Long id) {
        FilamentSpool spool = filamentSpoolRepository.findById(id).orElseThrow();
        spool.setLastDriedAt(LocalDateTime.now());
        filamentSpoolRepository.save(spool);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/label/{spoolId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateLabel(@PathVariable Long spoolId) throws Exception {
        FilamentSpool spool = filamentSpoolRepository.findById(spoolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] image = labelService.generateLabel(spool);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=label.png")
                .body(image);
    }
}
