package org.spon.edolcore.service.agent;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentHeartbeatMonitor {

    private final AgentStateService agentStateService;
    private final PrinterConnectivityStateService connectivityStateService;

    @Scheduled(fixedDelay = 10000)
    @ConditionalOnProperty(
            value = "edol.printer.connection-mode",
            havingValue = "AGENT"
    )
    public void monitor() {
        if (agentStateService.isOnline()) {
            connectivityStateService.setConnected();
        } else {
            connectivityStateService.setDisconnected();
        }
    }
}