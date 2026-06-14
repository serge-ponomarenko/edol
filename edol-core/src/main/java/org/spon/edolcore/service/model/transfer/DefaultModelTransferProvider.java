package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.printer.PrinterService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultModelTransferProvider implements ModelTransferProvider {

    private final PrinterService printerService;
    private final DirectModelTransferProvider directProvider;
    private final AgentModelTransferProvider agentProvider;

    @Override
    public void requestModel(UUID printerId) {
        Printer printer = printerService.getPrinter(printerId);

        switch (printer.getConnectionMode()) {
            case DIRECT -> directProvider.requestModel(printerId);
            case AGENT -> agentProvider.requestModel(printerId);
        }
    }
}
