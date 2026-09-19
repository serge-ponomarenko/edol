package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.VendorRepository;
import org.spon.edolhub.service.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/vendors")
public class VendorController {

    private final VendorRepository vendorRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vendors", vendorRepository.findAllByTenantIdOrderByName(tenantContext.getCurrentTenantId()));
        return "dashboard/vendors/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("vendor", new Vendor());
        return "dashboard/vendors/form";
    }

    @PostMapping
    public String save(@ModelAttribute Vendor vendor) {
        vendor.setTenant(tenantContext.getCurrentTenant());
        vendorRepository.save(vendor);
        return "redirect:/vendors";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Vendor vendor = vendorRepository.findByIdAndTenantId(id, tenantContext.getCurrentTenantId()).orElseThrow();
        model.addAttribute("vendor", vendor);
        return "dashboard/vendors/form";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        vendorRepository.delete(
                vendorRepository.findByIdAndTenantId(id, tenantContext.getCurrentTenantId()).orElseThrow()
        );
        return "redirect:/vendors";
    }
}
