package org.spon.edolcore.service.printer.runtime;

import java.util.Collection;
import java.util.UUID;

public interface PrinterRuntimeQueryService {

    boolean runtimeExists(UUID printerId);

    PrinterRuntime getRuntime(UUID printerId);

    Collection<PrinterRuntime> getRuntimes();

    Collection<UUID> getActivePrinterIds();

}
