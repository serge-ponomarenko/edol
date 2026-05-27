package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.AllocationResult;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.service.filament.FilamentMatchingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintAllocationSnapshotService {

    private final FilamentMatchingService filamentMatchingService;
    private final SpoolAllocationService spoolAllocationService;
    private final PrintAllocationPreviewService printAllocationPreviewService;

    public void createSnapshot(
            PrintJob job,
            PrinterState printerState
    ) {
        if (printerState.getFilaments() == null
                || printerState.getFilaments().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot create allocation snapshot without filament metadata"
            );
        }

        for (org.spon.edol.model.Filament filamentDto
                : printerState.getFilaments()) {
            Filament filament =
                    filamentMatchingService.match(
                            printerState,
                            filamentDto
                    );
            List<AllocationResult> allocations =
                    spoolAllocationService.previewAllocation(
                            filament,
                            filamentDto.getUsedGrams()
                    );
            printAllocationPreviewService.createOrUpdate(
                    job,
                    filament,
                    filamentDto.getAmsSlot(),
                    filamentDto.getUsedGrams(),
                    allocations
            );

        }

    }

}