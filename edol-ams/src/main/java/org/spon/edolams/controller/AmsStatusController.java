package org.spon.edolams.controller;

import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.AmsState;
import org.spon.edol.model.ExtTray;
import org.spon.edol.model.PrinterState;
import org.spon.edolams.model.AmsStatus;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/ams")
@Slf4j
public class AmsStatusController {

    private final RestClient edolCoreClient;
    private final RestClient edolDashboardClient;

    public AmsStatusController(
            @Qualifier("edolCoreRestClient") RestClient edolCoreClient,
            @Qualifier("edolDashboardRestClient") RestClient edolDashboardClient
    ) {
        this.edolCoreClient = edolCoreClient;
        this.edolDashboardClient = edolDashboardClient;
    }

    @GetMapping("/state")
    public AmsStatus getState() {
        try {
            PrinterState printerState = edolCoreClient.get()
                    .uri("/printer/state")
                    .retrieve()
                    .body(PrinterState.class);

            if (printerState != null && printerState.getAms() != null) {
                AmsState ams = printerState.getAms();

                AmsStatus amsStatus = new AmsStatus();
                amsStatus.setAmsId(ams.getAmsId());
                amsStatus.setHumidity(ams.getHumidity());
                amsStatus.setHumidityRaw(ams.getHumidityRaw());
                amsStatus.setTemperature(ams.getTemperature());
                amsStatus.setActiveSlot(ams.getActiveSlot());

                List<AmsStatus.Slot> slots = new ArrayList<>();

                for (AmsSlot amsSlot : ams.getSlots()) {
                    AmsStatus.Slot slot = new AmsStatus.Slot();
                    slot.setId(amsSlot.getId());
                    slot.setActive(amsStatus.getActiveSlot() == amsSlot.getId());
                    slot.setEmpty(amsSlot.isEmpty());
                    if (amsSlot.isEmpty()) {
                        slots.add(slot);
                        continue;
                    }
                    slot.setMaterial(amsSlot.getFilamentType());
                    String colorHex = "#" + amsSlot.getColor().substring(0, 6);
                    slot.setColor(colorHex);

                    String printerFilamentProfileId = amsSlot.getFilamentBrandIndex();
                    FilamentSpool spool = findSpool(printerFilamentProfileId, colorHex);
                    if (spool != null) {
                        slot.setVendor(spool.getFilament().getVendor().getName());
                        slot.setBrand(spool.getFilament().getBrand());
                        slot.setSpoolId(spool.getId());
                        slot.setRemaining(spool.getWeightRemaining());
                    }

                    slots.add(slot);
                }

                amsStatus.setSlots(slots);


                AmsStatus.Slot extTray = new AmsStatus.Slot();
                ExtTray ext = printerState.getExtTray();
                extTray.setId(255);
                extTray.setActive(printerState.isExternalSpoolUsed());
                extTray.setMaterial(ext.getFilamentType());
                String extColor = ext.getColor();
                String colorHex = extColor.isEmpty() ? "" : ("#" + extColor.substring(0, 6));
                extTray.setColor(colorHex);

                String printerFilamentProfileId = ext.getFilamentBrandIndex();
                FilamentSpool spool = findSpool(printerFilamentProfileId, colorHex);
                if (spool != null) {
                    extTray.setVendor(spool.getFilament().getVendor().getName());
                    extTray.setBrand(spool.getFilament().getBrand());
                    extTray.setSpoolId(spool.getId());
                    extTray.setRemaining(spool.getWeightRemaining());
                }

                amsStatus.setExtTray(extTray);

                return amsStatus;
            }

            return null;

        } catch (ResourceAccessException e) {
            log.error("EdolCore unavailable!");
            return null;
        }
    }

    private FilamentSpool findSpool(String printerFilamentProfileId, String colorHex) {
        return edolDashboardClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/spools/find")
                        .queryParam("printerFilamentProfileId", printerFilamentProfileId)
                        .queryParam("colorHex", colorHex)
                        .build())
                .retrieve()
                .body(FilamentSpool.class);
    }

}
