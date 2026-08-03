package org.spon.edolcore.service.printer.management.exception;

import java.util.UUID;

public class PrinterNotFoundException
        extends RuntimeException {

    public PrinterNotFoundException(UUID printerId) {
        super("Printer not found: " + printerId);
    }

    public PrinterNotFoundException(
            String displayId
    ) {
        super("Printer not found: " + displayId);
    }
}