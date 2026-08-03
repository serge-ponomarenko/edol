package org.spon.edolcore.service.printer.connectivity;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.agent.AgentStateService;
import org.spon.edolcore.service.printer.PrinterManagementService;
import org.spon.edolcore.service.printer.transport.BambuMqttConnectionManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterConnectivityProvider
        implements PrinterConnectivityProvider {

    private final PrinterManagementService printerManagementService;
    private final BambuMqttConnectionManager connectionManager;
    private final AgentStateService agentStateService;

    @Override
    public boolean isConnected(UUID printerId) {
        Printer printer =
                printerManagementService.getPrinter(printerId);

        return switch (printer.getConnectionMode()) {
            case DIRECT -> connectionManager.isConnected(printerId);

            case AGENT -> agentStateService.isOnline(printerId);
        };
    }
}
