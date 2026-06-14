package org.spon.edolcore.service.printer.telemetry;

import java.util.UUID;

public interface PrinterTelemetryProvider {

    void connect(UUID printerId);

    boolean isConnected(UUID printerId);
}