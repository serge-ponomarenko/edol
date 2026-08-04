package org.spon.edolcore.service.printer.runtime;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryPrinterRuntimeRegistry
        implements PrinterRuntimeRegistry {

    private final ConcurrentMap<UUID, PrinterRuntime> runtimes =
            new ConcurrentHashMap<>();

    @Override
    public PrinterRuntime get(UUID printerId) {
        PrinterRuntime runtime = runtimes.get(printerId);

        if (runtime == null) {
            throw new IllegalArgumentException(
                    "Runtime not found for printer: " + printerId
            );
        }

        return runtime;
    }

    @Override
    public Map<UUID, PrinterRuntime> getAll() {
        return runtimes;
    }

    @Override
    public PrinterRuntime create(UUID printerId) {
        return runtimes.computeIfAbsent(
                printerId,
                PrinterRuntime::new
        );
    }

    @Override
    public void remove(UUID printerId) {
        runtimes.remove(printerId);
    }

    @Override
    public boolean exists(UUID printerId) {
        return runtimes.containsKey(printerId);
    }
}