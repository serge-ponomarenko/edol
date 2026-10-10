package org.spon.edolams.service;

import java.util.UUID;

public class TerminalNotFoundException extends RuntimeException {
    public TerminalNotFoundException(UUID printerId) {
        super("No live terminal exists for printer " + printerId);
    }
}
