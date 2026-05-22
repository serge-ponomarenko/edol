package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.dto.FilamentDeletePreviewDto;
import org.spon.edoldashboard.model.dto.FilamentReplacePreviewDto;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.MaterialTypeRepository;
import org.spon.edoldashboard.repository.VendorRepository;
import org.spon.edoldashboard.service.filament.FilamentDeleteService;
import org.spon.edoldashboard.service.filament.FilamentReplaceService;
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
    private final FilamentReplaceService filamentReplaceService;
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

    @PostMapping("/replace/{sourceId}/{targetId}")
    public String replaceFilament(
            @PathVariable Long sourceId,
            @PathVariable Long targetId
    ) {
        filamentReplaceService.replace(sourceId, targetId);
        return "redirect:/filaments";
    }

    @GetMapping("/preview-replace/{id}")
    @ResponseBody
    public FilamentReplacePreviewDto preview(@PathVariable Long id) {
        return filamentReplaceService.preview(id);
    }
}