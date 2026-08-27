package org.spon.edolhub.service.spool;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.repository.PrintAllocationPreviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AllocationPreviewRuntimeSyncService {

    private final PrintAllocationPreviewRepository previewRepository;
    private final PrintAllocationPreviewMapper previewMapper;
    private final AllocationPreviewRuntimeCacheService
            runtimeCacheService;

    @Transactional(readOnly = true)
    public void refresh(
            UUID printJobId
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
