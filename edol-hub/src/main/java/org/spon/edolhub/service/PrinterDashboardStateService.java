package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.controller.PrinterStateController.PrinterStateEnriched;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.service.spool.AllocationPreviewRuntimeCacheService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterDashboardStateService {

    private final RestClient edolCoreClient;

    private final PrintRuntimeStateService
            runtimeStateService;
    private final AllocationPreviewRuntimeCacheService
            runtimeCacheService;

    public PrinterStateEnriched getState(UUID printerId) {
        try {
            PrinterState printerState =
                    edolCoreClient.get()
                            .uri("/api/printers/{printerId}/state", printerId)
                            .retrieve()
                            .body(PrinterState.class);

            if (printerState == null) {
                return null;
            }

            PrinterStateEnriched response = new PrinterStateEnriched();

            response.setPrinterState(printerState);

            PrintJob currentJob = runtimeStateService.getCurrentJob(printerId);

            if (currentJob == null) {
                response.setAllocationPreview(null);
                response.setAllocationPreviewPending(false);
                response.setJobId(null);

                return response;
            }

            response.setJobId(
                    currentJob.getPublicId()
            );

            boolean previewReady = runtimeStateService.isAllocationPreviewReady(printerId);

            response.setAllocationPreviewPending(
                    !previewReady
            );

            if (!previewReady) {
                return response;
            }

            PrintAllocationPreviewDto preview =
                    runtimeCacheService.getCurrentAllocationPreview(printerId);
            if (preview != null
                    && !preview.getPrintJobId().equals(currentJob.getId())) {
                preview = null;
            }
            response.setAllocationPreview(preview);

            return response;

        } catch (ResourceAccessException e) {
            return null;
        }
    }

}
