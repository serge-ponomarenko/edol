package org.spon.edolcore.service.printer.runtime;

import java.util.Map;
import java.util.UUID;

public interface PrinterRuntimeRegistry {

    PrinterRuntime get(UUID printerId);

    Map<UUID, PrinterRuntime> getAll();

    PrinterRuntime create(UUID printerId);

    void remove(UUID printerId);

    boolean exists(UUID printerId);
}