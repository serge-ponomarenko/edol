package org.spon.edolams.controller;

import lombok.extern.slf4j.Slf4j;
import org.spon.edolams.model.FilamentSpool;
import org.spon.edolams.model.Spool;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;


@RestController
@RequestMapping("/ams")
@Slf4j
public class SpoolController {

    private final RestClient edolCoreClient;
    private final RestClient edolHubClient;
    private final AmsSpoolChangerService amsSpoolChangerService;

    public SpoolController(
            @Qualifier("edolCoreRestClient") RestClient edolCoreClient,
            @Qualifier("edolHubRestClient") RestClient edolHubClient,
            AmsSpoolChangerService amsSpoolChangerService
    ) {
        this.edolCoreClient = edolCoreClient;
        this.edolHubClient = edolHubClient;
        this.amsSpoolChangerService = amsSpoolChangerService;
    }

    @GetMapping("/find")
    public ResponseEntity<Spool> findSpoolById(@RequestParam("id") Long id) {
        try {
            FilamentSpool filamentSpool = edolHubClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/spools/find-by-id")
                            .queryParam("id", id)
                            .build())
                    .retrieve()
                    .body(FilamentSpool.class);

            if (filamentSpool == null) {
                return ResponseEntity.noContent().build();
            }

            Spool spool = new Spool();
            spool.setSpoolId(filamentSpool.getId());
            spool.setBrand(filamentSpool.getFilament().getBrand());
            spool.setColor(filamentSpool.getFilament().getColorHex());
            spool.setMaterial(filamentSpool.getFilament().getMaterialType().getName());
            spool.setVendor(filamentSpool.getFilament().getVendor().getName());
            spool.setRemaining(filamentSpool.getWeightRemaining());

            amsSpoolChangerService.setSpoolScannedState(filamentSpool.getId());

            return ResponseEntity.ok(spool);

        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/set-spool")
    public ResponseEntity<String> setSpoolToAms(
            @RequestParam("id") Long id,
            @RequestParam("slot") Integer slot
    ) {
        edolHubClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/s/{id}/{slot}")
                        .build(id, slot))
                .retrieve()
                .toBodilessEntity();

        amsSpoolChangerService.resetScannedSpool();

        return ResponseEntity.ok(
                "Spool " + id + " assigned to AMS slot " + slot
        );
    }


}
