package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.LabelService;
import org.spon.edolhub.service.spool.FilamentSpoolService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
        // default: SEALED + ACTIVE
        if (status == null || status.isEmpty()) {
            status = List.of(
                    FilamentSpool.FilamentSpoolStatus.SEALED,
                    FilamentSpool.FilamentSpoolStatus.ACTIVE
            );
        }

        List<FilamentSpool> spools =
                filamentSpoolService.findFiltered(vendor, material, status);

        Map<Filament, List<FilamentSpool>> groupedSpools =
                spools.stream()
                        .collect(Collectors.groupingBy(
                                FilamentSpool::getFilament,
                                () -> new TreeMap<>(Comparator.comparing(Filament::getId)),
                                Collectors.toList()
                        ));

        model.addAttribute("groupedSpools", groupedSpools);
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

        model.addAttribute("spool", spool);
        model.addAttribute("filaments", filamentRepository.findAll());
        return "dashboard/filament-spools/form";
    }

    @GetMapping("/duplicate/{id}")
    public String duplicate(
            @PathVariable Long id,
            Model model
    ) {
        FilamentSpool source = filamentSpoolRepository.findById(id).orElseThrow();

        FilamentSpool spool = copyFilamentSpool(source);

        spool.setId(null);

        spool.setWeightRemaining(spool.getWeightTotal());

        spool.setStatus(FilamentSpool.FilamentSpoolStatus.SEALED);

        spool.setOpenedAt(null);
        spool.setLastUsedAt(null);
        spool.setLastDriedAt(null);

        model.addAttribute("spool", spool);
        model.addAttribute("filaments", filamentRepository.findAll());

        return "dashboard/filament-spools/form";
    }

    @PostMapping
    public String save(
            @ModelAttribute FilamentSpool spool,
            @RequestParam(defaultValue = "1") Integer quantity
    ) {
        if (spool.getWeightRemaining() == null) {
            spool.setWeightRemaining(spool.getWeightTotal());
        }

        if (spool.getId() != null) {
            filamentSpoolRepository.save(spool);
        } else {
            for (int i = 0; i < quantity; i++) {
                FilamentSpool copy = copyFilamentSpool(spool);
                filamentSpoolRepository.save(copy);
            }
        }

        return "redirect:/filament-spools";
    }

    private static @NonNull FilamentSpool copyFilamentSpool(FilamentSpool spool) {
        FilamentSpool copy = new FilamentSpool();

        copy.setFilament(spool.getFilament());

        copy.setWeightTotal(spool.getWeightTotal());
        copy.setWeightRemaining(spool.getWeightRemaining());

        copy.setPrice(spool.getPrice());

        copy.setStoreUrl(spool.getStoreUrl());

        copy.setOpenedAt(spool.getOpenedAt());
        copy.setLastUsedAt(spool.getLastUsedAt());
        copy.setLastDriedAt(spool.getLastDriedAt());

        copy.setStatus(spool.getStatus());

        copy.setComment(spool.getComment());
        return copy;
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
