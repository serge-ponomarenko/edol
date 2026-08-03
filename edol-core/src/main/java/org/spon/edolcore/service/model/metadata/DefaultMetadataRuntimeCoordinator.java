package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultMetadataRuntimeCoordinator
        implements MetadataRuntimeCoordinator {

    private final MetadataAcquisitionService metadataAcquisitionService;

    @Override
    public void start(UUID printerId) {
        metadataAcquisitionService.start(printerId);
    }

    @Override
    public void stop(UUID printerId) {
        metadataAcquisitionService.stop(printerId);
    }
}