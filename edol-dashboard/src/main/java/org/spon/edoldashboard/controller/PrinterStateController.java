package org.spon.edoldashboard.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.PrinterState;
import org.spon.edoldashboard.model.dto.MaintenanceStatusDto;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.model.entity.PrintJob;
import org.spon.edoldashboard.model.entity.PrinterStats;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.service.FilamentSpoolService;
import org.spon.edoldashboard.service.MaintenanceService;
import org.spon.edoldashboard.service.PrintJobService;
import org.spon.edoldashboard.service.PrinterStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final RestClient edolCoreClient;
    private final FilamentSpoolService filamentSpoolService;
    private final PrinterStatsService printerStatsService;
    private final MaintenanceService maintenanceService;
    private final FilamentRepository filamentRepository;
    private final PrintJobService printJobService;

    private PrinterStateEnriched printerStateEnriched;

    @GetMapping("/state")
    public PrinterStateEnriched getState() {
        try {
            PrinterState printerState = edolCoreClient.get()
                    .uri("/printer/state")
                    .retrieve()
                    .body(PrinterState.class);

            if (printerStateEnriched == null) {
                printerStateEnriched = new PrinterStateEnriched();
            }

            printerStateEnriched.setPrinterState(printerState);

            if (
                    printerState != null && printerState.getFilaments() != null
                            && !printerStateEnriched.getSessionId().equals(printerState.getSessionId())
            ) {
                PrintJob currentJob = printJobService.getCurrentJob();
                if (currentJob != null) {
                    printerStateEnriched.setJobId(currentJob.getId());
                }
                printerStateEnriched.setSessionId(printerState.getSessionId());

                printerStateEnriched.setFilamentCosts(new HashMap<>());

                printerState.getFilaments().forEach(filament -> {
                    String filamentColor = filament.getColor();

                    if (filament.getAmsSlot() != null && filament.getAmsSlot() != -1) {
                        AmsSlot amsSlot = printerState.getAms().getSlots().get(filament.getAmsSlot());
                        if (!amsSlot.getColor().substring(0, 6).equals(filament.getColor().substring(1))) {
                            filamentColor = "#" + amsSlot.getColor().substring(0, 6);
                        }
                    }
                    if (printerState.isExternalSpoolUsed()) {
                        if (!printerState.getExtTray().getColor().substring(0, 6).equals(filament.getColor().substring(1))) {
                            filamentColor = "#" + printerState.getExtTray().getColor().substring(0, 6);
                        }
                    }

                    filamentRepository
                            .findFirstByFullIdAndColorHex(filament.getFullId(), filamentColor)
                            .flatMap(f ->
                                    filamentSpoolService
                                            .findSpool(f.getId(), FilamentSpool.FilamentSpoolStatus.ACTIVE))
                            .ifPresent(spool -> {
                                BigDecimal costPerGram =
                                        spool.getPrice().divide(
                                                BigDecimal.valueOf(spool.getWeightTotal()),
                                                4,
                                                RoundingMode.HALF_UP
                                        );

                                printerStateEnriched.getFilamentCosts().put(
                                        filament.getId(),
                                        costPerGram
                                                .multiply(BigDecimal.valueOf(filament.getUsedGrams()))
                                                .doubleValue()
                                );
                            });

                });

            }

            return printerStateEnriched;

        } catch (ResourceAccessException e) {
            log.error("EdolCore unavailable!");
            return null;
        }
    }

    @GetMapping("/stats")
    public PrinterStats getStats() {
        return printerStatsService.getStats();
    }

    @GetMapping("/alerts")
    public List<MaintenanceStatusDto> getMaintenanceAlerts() {
        return maintenanceService.getMaintenanceStatus().stream().filter(MaintenanceStatusDto::isDue).toList();
    }

    @Data
    public static class PrinterStateEnriched {
        Long jobId;
        String sessionId = "";
        PrinterState printerState;
        Map<Integer, Double> filamentCosts;
    }
}
