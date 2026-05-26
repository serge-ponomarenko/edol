package org.spon.edolhub.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.FilamentCostPreviewDto;
import org.spon.edolhub.model.dto.MaintenanceStatusDto;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.service.MaintenanceService;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterStatsService;
import org.spon.edolhub.service.spool.PrintAllocationPreviewQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final RestClient edolCoreClient;
    private final PrinterStatsService printerStatsService;
    private final MaintenanceService maintenanceService;
    private final PrintJobService printJobService;
    private final PrintAllocationPreviewQueryService printAllocationPreviewQueryService;

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
                    printerState != null
                            && printerState.getFilaments() != null
                            && !printerStateEnriched
                            .getSessionId()
                            .equals(printerState.getSessionId())
            ) {

                PrintJob currentJob = printJobService.getCurrentJob();
                if (currentJob != null) {
                    Map<Integer, FilamentCostPreviewDto> filamentUsage = new HashMap<>();

                    boolean previewReady = true;

                    for (org.spon.edol.model.Filament filament
                            : printerState.getFilaments()) {
                        FilamentCostPreviewDto preview =
                                printAllocationPreviewQueryService
                                        .getPreview(
                                                currentJob.getId(),
                                                (long) filament.getId()
                                        );

                        if (preview == null) {
                            previewReady = false;
                            break;
                        }

                        filamentUsage.put(
                                filament.getId(),
                                preview
                        );

                    }

                    if (previewReady) {
                        printerStateEnriched.setJobId(currentJob.getId());
                        printerStateEnriched.setSessionId(printerState.getSessionId());
                        printerStateEnriched.setFilamentUsage(filamentUsage);
                    }

                }

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
        Map<Integer, FilamentCostPreviewDto> filamentUsage;
    }
}
