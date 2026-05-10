package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/spools")
public class SpoolApiController {

    private final FilamentSpoolRepository spoolRepository;

    @GetMapping
    public List<FilamentSpool> spools() {
        return spoolRepository.findAll();
    }

    @GetMapping("/find")
    public FilamentSpool findSpool(@RequestParam("fullId") String fullId, @RequestParam("colorHex") String colorHex) {
        return null;
    }

}
