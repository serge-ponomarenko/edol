package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.transport.BambuMqttConnectionManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectPrinterTelemetryProvider
        implements PrinterTelemetryProvider {

    private final BambuMqttConnectionManager connectionManager;

    @Override
    public void connect(UUID printerId) {
        connectionManager.connect(printerId);
    }

    @Override
    public boolean isConnected(UUID printerId) {
        return connectionManager.isConnected(printerId);
    }
}