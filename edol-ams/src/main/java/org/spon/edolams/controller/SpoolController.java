package org.spon.edolams.controller;

import lombok.extern.slf4j.Slf4j;
import org.spon.edolams.model.HubFilamentSpool;
import org.spon.edolams.model.Spool;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.AmsPrinterTenantResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;


@RestController
@RequestMapping("/ams")
@Slf4j
public class SpoolController {

    private final RestClient edolCoreClient;
    private final RestClient edolHubClient;
    private final AmsSpoolChangerService amsSpoolChangerService;
    private final AmsPrinterTenantResolver tenantResolver;

    public SpoolController(
            @Qualifier("edolCoreRestClient") RestClient edolCoreClient,
            @Qualifier("edolHubRestClient") RestClient edolHubClient,
            AmsSpoolChangerService amsSpoolChangerService,
            AmsPrinterTenantResolver tenantResolver
    ) {
        this.edolCoreClient = edolCoreClient;
        this.edolHubClient = edolHubClient;
        this.amsSpoolChangerService = amsSpoolChangerService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/find")
    public ResponseEntity<Spool> findSpoolById(@RequestParam("id") Long id,
                                               @RequestParam UUID printerId) {
        return tenantResolver.withPrinter(printerId, () -> findSpoolForTrustedPrinter(id, printerId));
    }

    private ResponseEntity<Spool> findSpoolForTrustedPrinter(Long id, UUID printerId) {
        try {
            HubFilamentSpool filamentSpool = edolHubClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/spools/find-by-id")
                            .queryParam("id", id)
                            .build())
                    .retrieve()
                    .body(HubFilamentSpool.class);

            if (filamentSpool == null) {
                return ResponseEntity.noContent().build();
            }

            Spool spool = toAmsSpool(filamentSpool);

            amsSpoolChangerService.setSpoolScannedState(printerId, filamentSpool.id());

            return ResponseEntity.ok(spool);

        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("Unable to resolve Hub spool {} for AMS printer {}", id, printerId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    static Spool toAmsSpool(HubFilamentSpool filamentSpool) {
        Spool spool = new Spool();
        spool.setSpoolId(filamentSpool.id());
        spool.setBrand(filamentSpool.filament().brand());
        spool.setColor(filamentSpool.filament().colorHex());
        spool.setMaterial(filamentSpool.filament().materialType().name());
        spool.setVendor(filamentSpool.filament().vendor().name());
        spool.setRemaining(filamentSpool.weightRemaining().intValue());
        return spool;
    }

    @GetMapping("/set-spool")
    public ResponseEntity<String> setSpoolToAms(
            @RequestParam("id") Long id,
            @RequestParam("slot") Integer slot,
            @RequestParam UUID printerId
    ) {
        return tenantResolver.withPrinter(printerId, () -> {
            edolHubClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/s/{printerId}/{spoolId}/{slot}")
                            .build(printerId, id, slot))
                    .retrieve()
                    .toBodilessEntity();

            amsSpoolChangerService.resetScannedSpool(printerId);
            return ResponseEntity.ok(
                    "Spool " + id + " assigned to AMS slot " + slot + " for printer " + printerId
            );
        });
    }


}
