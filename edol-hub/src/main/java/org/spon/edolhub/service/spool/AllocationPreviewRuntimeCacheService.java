package org.spon.edolhub.service.spool;

import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.spon.edolhub.service.TransactionalRuntimeMap;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AllocationPreviewRuntimeCacheService {

    private final TransactionalRuntimeMap<PrintAllocationPreviewDto> previews = new TransactionalRuntimeMap<>();

    public PrintAllocationPreviewDto getCurrentAllocationPreview(UUID printerId) {
        return previews.get(printerId);
    }

    public void setCurrentAllocationPreview(UUID printerId, PrintAllocationPreviewDto preview) {
        previews.put(printerId, preview);
    }

}
