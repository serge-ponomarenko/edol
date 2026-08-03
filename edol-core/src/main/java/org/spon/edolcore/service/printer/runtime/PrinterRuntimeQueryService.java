package org.spon.edolcore.service.printer.runtime;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PrinterRuntimeQueryService {

    boolean runtimeExists(UUID printerId);

    PrinterRuntimeContext getRuntimeContext(UUID printerId);

    Map<UUID, PrinterRuntimeContext> getRuntimeContexts();

    Collection<UUID> getActivePrinterIds();

}
