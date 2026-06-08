package org.spon.edolcore.service.printer.connectivity;

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
public class DirectPrinterConnectivityProvider
        implements PrinterConnectivityProvider {

    private final BambuMqttClient bambuMqttClient;

    @Override
    public boolean isConnected() {
        return bambuMqttClient.isConnected();
    }
}