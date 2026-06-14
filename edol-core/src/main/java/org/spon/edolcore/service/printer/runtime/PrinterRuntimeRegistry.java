package org.spon.edolcore.service.printer.runtime;

import java.util.Collection;
import java.util.UUID;

public interface PrinterRuntimeRegistry {

    PrinterRuntimeContext get(UUID printerId);

    PrinterRuntimeContext getOrCreate(UUID printerId);

    Collection<PrinterRuntimeContext> getAll();

    PrinterRuntimeContext create(UUID printerId);

    void remove(UUID printerId);

    boolean exists(UUID printerId);
}