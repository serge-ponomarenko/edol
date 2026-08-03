package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.model.metadata.MetadataRuntimeCoordinator;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterRuntimeLifecycleService
        implements PrinterRuntimeLifecycleService {

    private final PrinterRuntimeRegistry runtimeRegistry;
    private final MetadataRuntimeCoordinator metadataRuntimeCoordinator;

    @Override
    public void startRuntime(UUID printerId) {
        createRuntime(printerId);
    }

    @Override
    public void stopRuntime(UUID printerId) {
        metadataRuntimeCoordinator.stop(
                printerId
        );

        removeRuntime(
                printerId
        );
    }

    private void createRuntime(UUID printerId) {
        runtimeRegistry.create(printerId);
    }

    private void removeRuntime(UUID printerId) {
        runtimeRegistry.remove(printerId);
    }

    @Override
    public boolean runtimeExists(UUID printerId) {
        return runtimeRegistry.exists(printerId);
    }

    @Override
    public Map<UUID, PrinterRuntimeContext> getRuntimeContexts() {
        return runtimeRegistry.getAll();
    }

    @Override
    public Collection<UUID> getActivePrinterIds() {
        return runtimeRegistry.getAll()
                .keySet()
                .stream().toList();
    }
}