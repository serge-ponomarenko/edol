package org.spon.edolcore.service.printer.runtime;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PrinterRuntimeLifecycleService {

    void startRuntime(UUID printerId);

    void stopRuntime(UUID printerId);

    boolean runtimeExists(UUID printerId);

    Map<UUID, PrinterRuntimeContext> getRuntimeContexts();

    Collection<UUID> getActivePrinterIds();
}