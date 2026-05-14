package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.springframework.http.ResponseEntity;
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
    private final FilamentRepository filamentRepository;

    @GetMapping
    public List<FilamentSpool> spools() {
        return spoolRepository.findAll();
    }

    @GetMapping("/find")
    public ResponseEntity<FilamentSpool> findSpool(
            @RequestParam("printerFilamentProfileId") String printerFilamentProfileId,
            @RequestParam("colorHex") String colorHex
    ) {
        return filamentRepository.findFirstByPrinterFilamentProfileIdAndColorHex(
                        printerFilamentProfileId,
                        colorHex
                )
                .flatMap(filament -> spoolRepository.findFirstByFilamentIdAndStatus(
                        filament.getId(),
                        FilamentSpool.FilamentSpoolStatus.ACTIVE
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/find-by-id")
    public ResponseEntity<FilamentSpool> findSpoolById(
            @RequestParam("id") Long id
    ) {
        return spoolRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

}
