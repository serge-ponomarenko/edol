package org.spon.edoldashboard.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.repository.FilamentSpoolRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/s")
public class SpoolChangeController {

    private final RestTemplate restTemplate;
    private final FilamentSpoolRepository filamentSpoolRepository;

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @PostMapping("/{spoolId}/{trayId}")
    public String spoolChange(@PathVariable Long spoolId,
                              @PathVariable Integer trayId,
                              RedirectAttributes redirectAttributes) {
        if (trayId < 0 || (trayId > 3 && trayId != 254 && trayId != 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid trayId");
        }

        int amsId = 0;
        if (trayId == 254 || trayId == 255) {
            amsId = trayId;
        }

        FilamentSpool filamentSpool = filamentSpoolRepository.findById(spoolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spool not found"));

        var filament = filamentSpool.getFilament();

        String color = filament.getColorHex();
        if (color == null || !color.startsWith("#") || color.length() != 7) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid color");
        }

        String url = edolCoreUrl + "/printer/request/spool-change";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("amsId", amsId);
        body.put("trayId", trayId);
        body.put("trayInfoIdx", filament.getPrinterFilamentProfileId());
        body.put("trayColor", color.substring(1) + "FF");
        body.put("trayType", filament.getMaterialType().getName());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        // 🔔 Flash message
        redirectAttributes.addFlashAttribute("toastMessage",
                filament.getBrand() + " → Tray " + (trayId + 1));

        redirectAttributes.addFlashAttribute("toastColor",
                color);

        return "redirect:/s/" + spoolId;
    }

}
