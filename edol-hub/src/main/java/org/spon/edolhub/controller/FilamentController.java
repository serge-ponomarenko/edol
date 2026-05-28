package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.FilamentDeletePreviewDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.repository.FilamentRepository;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.filament.FilamentDeleteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/filaments")
public class FilamentController {

    private final FilamentRepository filamentRepository;
    private final VendorRepository vendorRepository;
    private final MaterialTypeRepository materialTypeRepository;
    private final FilamentDeleteService filamentDeleteService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("filaments", filamentRepository.findAll());

        return "dashboard/filaments/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("filament", new Filament());
        model.addAttribute("vendors", vendorRepository.findAll());
        model.addAttribute("materialTypes", materialTypeRepository.findAll());

        return "dashboard/filaments/form";
    }

    @PostMapping
    public String save(@ModelAttribute Filament filament) {
        filament.setColorHex(filament.getColorHex().toUpperCase());
        filamentRepository.save(filament);

        return "redirect:/filaments";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Filament filament = filamentRepository.findById(id).orElseThrow();

        model.addAttribute("filament", filament);
        model.addAttribute("vendors", vendorRepository.findAll());
        model.addAttribute("materialTypes", materialTypeRepository.findAll());

        return "dashboard/filaments/form";
    }

    @GetMapping("/preview-delete/{id}")
    @ResponseBody
    public FilamentDeletePreviewDto previewDelete(@PathVariable Long id) {
        return filamentDeleteService.preview(id);
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        FilamentDeletePreviewDto preview = filamentDeleteService.preview(id);

        if (preview.hasDependencies()) {
            ra.addFlashAttribute(
                    "deleteError",
                    "Filament is used by other entities"
            );
            return "redirect:/filaments";
        }

        filamentRepository.deleteById(id);

        return "redirect:/filaments";
    }

}