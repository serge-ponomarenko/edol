package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PrinterRuntimeContext {

    private final UUID printerId;

    public PrinterRuntimeContext(UUID printerId) {
        this.printerId = printerId;
    }
}