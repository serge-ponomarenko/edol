package org.spon.edolcore.service.printer.telemetry;

import java.util.UUID;

public interface PrinterTelemetryProvider {

    void connect(UUID printerId);

    void disconnect(UUID printerId);

    boolean isConnected(UUID printerId);

}