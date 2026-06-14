package org.spon.edolcore.service.printer.runtime;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryPrinterRuntimeRegistry
        implements PrinterRuntimeRegistry {

    private final ConcurrentMap<UUID, PrinterRuntimeContext> contexts =
            new ConcurrentHashMap<>();

    @Override
    public PrinterRuntimeContext get(UUID printerId) {
        PrinterRuntimeContext context = contexts.get(printerId);

        if (context == null) {
            throw new IllegalArgumentException(
                    "Runtime context not found for printer: " + printerId
            );
        }

        return context;
    }

    @Override
    public PrinterRuntimeContext getOrCreate(UUID printerId) {
        return contexts.computeIfAbsent(
                printerId,
                PrinterRuntimeContext::new
        );
    }

    @Override
    public Collection<PrinterRuntimeContext> getAll() {
        return contexts.values();
    }

    @Override
    public PrinterRuntimeContext create(UUID printerId) {
        return contexts.computeIfAbsent(
                printerId,
                PrinterRuntimeContext::new
        );
    }

    @Override
    public void remove(UUID printerId) {
        contexts.remove(printerId);
    }

    @Override
    public boolean exists(UUID printerId) {
        return contexts.containsKey(printerId);
    }
}