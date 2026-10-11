package org.spon.edolcore.service.printer.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
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
