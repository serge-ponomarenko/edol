package org.spon.edolcore.service.printer.telemetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "AGENT"
)
public class AgentPrinterTelemetryProvider
        implements PrinterTelemetryProvider {

    @Override
    public void connect() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}