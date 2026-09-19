package org.spon.edolhub.service.spool;

import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AllocationPreviewRuntimeCacheService {

    private final ConcurrentMap<UUID, PrintAllocationPreviewDto> previews = new ConcurrentHashMap<>();

    public PrintAllocationPreviewDto getCurrentAllocationPreview(UUID printerId) {
        return previews.get(printerId);
    }

    public void setCurrentAllocationPreview(UUID printerId, PrintAllocationPreviewDto preview) {
        if (preview == null) {
            previews.remove(printerId);
        } else {
            previews.put(printerId, preview);
        }
    }

}
