package org.spon.edolcore.service.printer.runtime;

import java.util.Map;
import java.util.UUID;

public interface PrinterRuntimeContextProvider {

    PrinterRuntimeContext getContext(UUID printerId);

    Map<UUID, PrinterRuntimeContext> getAllContexts();
}
