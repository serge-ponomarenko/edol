package org.spon.edolams.service;

import java.util.UUID;

public class UnmappedAmsPrinterException extends IllegalArgumentException {

    public UnmappedAmsPrinterException(UUID printerId) {
        super("AMS printer is not mapped to a tenant: " + printerId);
    }
}
