package org.spon.edolcore.service.agent;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeCatalogEnumerator;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "edol-core.runtime.printer-runtime-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AgentHeartbeatMonitor {

    private final AgentStateService agentStateService;
    private final PrinterConnectivityStateService connectivityStateService;
    private final PrinterManagementService printerManagementService;
    private final CoreRuntimeCatalogEnumerator runtimeCatalogEnumerator;
    private final CoreRuntimeTenantExecutor runtimeTenantExecutor;

    @Scheduled(fixedDelay = 10000, initialDelay = 4000)
    public void monitor() {
        for (var entry : runtimeCatalogEnumerator.enabledPrinters()) {
            runtimeTenantExecutor.execute(entry, () -> monitor(entry.printerId()));
        }
    }

    private void monitor(UUID printerId) {
        var printer = printerManagementService.getPrinter(printerId);
            if (printer.getConnectionMode() == PrinterConnectionMode.AGENT
                    && !agentStateService.isOfflineSuppressed(printerId)) {
                if (agentStateService.isOnline(printerId)) {
                    connectivityStateService.setConnected(printerId);
                } else {
                    connectivityStateService.setDisconnected(printerId);
                }
            }
    }
}
