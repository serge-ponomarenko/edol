package org.spon.edolcore.service.printer.runtime;

import java.util.Map;
import java.util.UUID;

public interface PrinterRuntimeRegistry {

    PrinterRuntimeContext get(UUID printerId);

    Map<UUID, PrinterRuntimeContext> getAll();

    PrinterRuntimeContext create(UUID printerId);

    void remove(UUID printerId);

    boolean exists(UUID printerId);
}