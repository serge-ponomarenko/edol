package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.MaterialType;
import org.spon.edolhub.repository.MaterialTypeRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/materials")
public class MaterialTypeController {

    private final MaterialTypeRepository materialRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("materials", materialRepository.findAll());

        return "dashboard/materials/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("material", new MaterialType());

        return "dashboard/materials/form";
    }

    @PostMapping
    public String save(@ModelAttribute MaterialType material) {
        materialRepository.save(material);

        return "redirect:/materials";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        MaterialType material = materialRepository.findById(id).orElseThrow();

        model.addAttribute("material", material);

        return "dashboard/materials/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        materialRepository.deleteById(id);

        return "redirect:/materials";
    }

}