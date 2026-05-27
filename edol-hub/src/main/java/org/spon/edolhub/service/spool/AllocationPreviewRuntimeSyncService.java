package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AllocationPreviewRuntimeSyncService {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintAllocationPreviewMapper previewMapper;
    private final AllocationPreviewRuntimeCacheService
            runtimeCacheService;

    public void refresh(
            Long printJobId
    ) {
        previewRepository
                .findByPrintJobId(printJobId)
                .ifPresent(preview ->
                        runtimeCacheService
                                .setCurrentAllocationPreview(
                                        previewMapper
                                                .toDto(preview)
                                )
                );
    }

}
