package org.spon.edolcore.service.printer.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PrinterRuntimeNotFoundException extends RuntimeException {

    public PrinterRuntimeNotFoundException(UUID printerId) {
        super("Runtime not found for printer: " + printerId);
    }
}
