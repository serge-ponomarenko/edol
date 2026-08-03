package org.spon.edolcore.service.printer.telemetry;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentPrinterTelemetryProvider
        implements PrinterTelemetryProvider {

    @Override
    public void connect(UUID printerId) {
        // no-op
    }

    @Override
    public void disconnect(UUID printerId) {
        // no-op
    }

    @Override
    public boolean isConnected(UUID printerId) {
        return true;
    }
}