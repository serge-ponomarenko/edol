package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterRuntimeQueryService
        implements PrinterRuntimeQueryService {

    private final PrinterRuntimeRegistry runtimeRegistry;

    @Override
    public boolean runtimeExists(UUID printerId) {
        return runtimeRegistry.exists(printerId);
    }

    @Override
    public PrinterRuntime getRuntime(UUID printerId) {
        return runtimeRegistry.get(printerId);
    }

    @Override
    public Collection<PrinterRuntime> getRuntimes() {
        return runtimeRegistry
                .getAll()
                .values();
    }

    @Override
    public Collection<UUID> getActivePrinterIds() {
        return runtimeRegistry.getAll()
                .keySet()
                .stream()
                .toList();
    }

}