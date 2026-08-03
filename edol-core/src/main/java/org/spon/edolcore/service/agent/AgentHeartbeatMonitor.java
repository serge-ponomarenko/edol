package org.spon.edolcore.service.agent;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.printer.PrinterManagementService;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentHeartbeatMonitor {

    private final AgentStateService agentStateService;
    private final PrinterConnectivityStateService connectivityStateService;
    private final PrinterManagementService printerManagementService;

    @Scheduled(fixedDelay = 10000, initialDelay = 4000)
    public void monitor() {
        for (Printer printer : printerManagementService.getEnabledPrinters()) {
            if (printer.getConnectionMode() == PrinterConnectionMode.AGENT) {
                UUID printerId = printer.getId();

                if (!agentStateService.isOfflineSuppressed(printerId)) {
                    if (agentStateService.isOnline(printerId)) {
                        connectivityStateService.setConnected(printerId);
                    } else {
                        connectivityStateService.setDisconnected(printerId);
                    }
                }
            }
        }
    }
}
