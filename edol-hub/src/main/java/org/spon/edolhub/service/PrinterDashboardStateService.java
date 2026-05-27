package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.controller.PrinterStateController.PrinterStateEnriched;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.service.spool.AllocationPreviewRuntimeCacheService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterDashboardStateService {

    private final RestClient edolCoreClient;

    private final PrintRuntimeStateService
            runtimeStateService;
    private final AllocationPreviewRuntimeCacheService
            runtimeCacheService;

    public PrinterStateEnriched getState() {
        try {
            PrinterState printerState =
                    edolCoreClient.get()
                            .uri("/printer/state")
                            .retrieve()
                            .body(PrinterState.class);

            if (printerState == null) {
                return null;
            }

            PrinterStateEnriched response = new PrinterStateEnriched();

            response.setPrinterState(printerState);

            PrintJob currentJob = runtimeStateService.getCurrentJob();

            if (currentJob == null) {
                response.setAllocationPreview(null);
                response.setAllocationPreviewPending(false);
                response.setJobId(null);

                return response;
            }

            response.setJobId(
                    currentJob.getId()
            );

            boolean previewReady =
                    runtimeStateService
                            .isAllocationPreviewReady();

            response.setAllocationPreviewPending(
                    !previewReady
            );

            if (!previewReady) {
                return response;
            }

            response.setAllocationPreview(
                    runtimeCacheService
                            .getCurrentAllocationPreview()
            );

            return response;

        } catch (ResourceAccessException e) {
            return null;
        }
    }

}