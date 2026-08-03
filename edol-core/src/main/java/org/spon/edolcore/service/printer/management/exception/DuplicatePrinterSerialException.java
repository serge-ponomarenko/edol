package org.spon.edolcore.service.printer.management.exception;

public class DuplicatePrinterSerialException
        extends RuntimeException {

    public DuplicatePrinterSerialException(String serial) {
        super("Printer serial already exists: " + serial);
    }
}