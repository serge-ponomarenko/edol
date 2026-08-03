package org.spon.edolcore.service.model.metadata;

import java.util.UUID;

public interface MetadataRuntimeCoordinator {

    void start(UUID printerId);

    void stop(UUID printerId);
}