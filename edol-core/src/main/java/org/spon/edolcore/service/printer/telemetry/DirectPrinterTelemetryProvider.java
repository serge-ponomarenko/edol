package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.transport.BambuMqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "DIRECT",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class DirectPrinterTelemetryProvider
        implements PrinterTelemetryProvider {

    private final BambuMqttClient bambuMqttClient;

    @Override
    public void connect() {
        bambuMqttClient.connect();
    }

    @Override
    public boolean isConnected() {
        return bambuMqttClient.isConnected();
    }
}