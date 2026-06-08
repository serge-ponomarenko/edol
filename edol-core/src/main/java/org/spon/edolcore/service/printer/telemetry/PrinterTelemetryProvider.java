package org.spon.edolcore.service.printer.telemetry;

public interface PrinterTelemetryProvider {

    void connect();

    boolean isConnected();
}