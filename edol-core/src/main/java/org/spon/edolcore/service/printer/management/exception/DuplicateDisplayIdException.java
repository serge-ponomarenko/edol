package org.spon.edolcore.service.printer.management.exception;

public class DuplicateDisplayIdException
        extends RuntimeException {

    public DuplicateDisplayIdException(String displayId) {
        super("Printer displayId already exists: " + displayId);
    }
}