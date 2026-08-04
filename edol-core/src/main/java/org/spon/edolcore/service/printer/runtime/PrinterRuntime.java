package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PrinterRuntime {

    private final UUID printerId;

    private final PrinterRuntimeContext context;

    public PrinterRuntime(UUID printerId) {
        this.printerId = printerId;
        this.context = new PrinterRuntimeContext(printerId);
    }

}
