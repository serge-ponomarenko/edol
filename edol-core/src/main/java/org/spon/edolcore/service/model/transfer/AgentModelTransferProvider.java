package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.exception.ModelNotLoadedException;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "AGENT"
)
@Slf4j
@RequiredArgsConstructor
public class AgentModelTransferProvider implements ModelTransferProvider {

    private final PrinterStateService printerStateService;
    private final AgentCommandGateway agentCommandGateway;

    @Override
    public void requestModel() {
        PrinterState state = printerStateService.getState();

        String fileName = state.getCurrentFile();

        if (fileName == null || fileName.isEmpty())
            throw new ModelNotLoadedException();

        agentCommandGateway.disableSnapshotScheduler();
        agentCommandGateway.uploadModel(fileName);
    }
}
