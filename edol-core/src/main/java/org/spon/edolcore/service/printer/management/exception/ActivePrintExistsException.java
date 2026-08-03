package org.spon.edolcore.service.printer.management.exception;

import java.util.UUID;

public class ActivePrintExistsException
        extends RuntimeException {

    public ActivePrintExistsException(UUID printerId) {
        super("Printer has active print: " + printerId);
    }
}