package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.model.metadata.MetadataRuntimeCoordinator;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterRuntimeLifecycleService
        implements PrinterRuntimeLifecycleService {

    private final PrinterRuntimeRegistry runtimeRegistry;
    private final MetadataRuntimeCoordinator metadataRuntimeCoordinator;

    @Override
    public void createRuntime(UUID printerId) {
        runtimeRegistry.create(printerId);
    }

    @Override
    public void startRuntime(UUID printerId) {
        // TODO: Runtime startup will be implemented in the next milestone.
    }

    @Override
    public void stopRuntime(UUID printerId) {
        metadataRuntimeCoordinator.stop(printerId);
    }

    @Override
    public void destroyRuntime(UUID printerId) {
        runtimeRegistry.remove(printerId);
    }

    @Override
    public void restartRuntime(UUID printerId) {
        stopRuntime(printerId);
        startRuntime(printerId);
    }

}