package org.spon.edolcore.service.printer.connectivity;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.AgentStateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "AGENT"
)
@RequiredArgsConstructor
public class AgentPrinterConnectivityProvider
        implements PrinterConnectivityProvider {

    private final AgentStateService agentStateService;

    @Override
    public boolean isConnected() {
        return agentStateService.isOnline();
    }
}