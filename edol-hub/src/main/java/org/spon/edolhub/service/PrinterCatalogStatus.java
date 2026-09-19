package org.spon.edolhub.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Getter
public class PrinterCatalogStatus {

    private volatile boolean coreAvailable;
    private volatile boolean migrationBlocked;
    private volatile String message = "Printer catalog has not been synchronized";
    private volatile Instant lastSuccessfulSync;

    public void synchronizedSuccessfully() {
        coreAvailable = true;
        migrationBlocked = false;
        message = null;
        lastSuccessfulSync = Instant.now();
    }

    public void coreUnavailable(String reason) {
        coreAvailable = false;
        message = reason;
    }

    public void migrationBlocked(String reason) {
        coreAvailable = true;
        migrationBlocked = true;
        message = reason;
    }
}
