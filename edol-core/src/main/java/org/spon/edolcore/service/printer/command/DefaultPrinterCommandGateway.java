package org.spon.edolcore.service.printer.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.model.dto.SpoolChangeRequestDto;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterCommandGateway
        implements PrinterCommandGateway {

    private final PrinterManagementService printerManagementService;

    private final DirectPrinterCommandGateway directGateway;
    private final AgentPrinterCommandGateway agentGateway;

    @Override
    public void pause(UUID printerId) {
        if (isDirect(printerId)) {
            directGateway.pause(printerId);
            return;
        }

        agentGateway.pause(printerId);
    }

    @Override
    public void resume(UUID printerId) {
        if (isDirect(printerId)) {
            directGateway.resume(printerId);
            return;
        }

        agentGateway.resume(printerId);
    }

    @Override
    public void stop(UUID printerId) {
        if (isDirect(printerId)) {
            directGateway.stop(printerId);
            return;
        }

        agentGateway.stop(printerId);
    }

    @Override
    public void setPrintSpeed(UUID printerId, int level) {
        if (isDirect(printerId)) {
            directGateway.setPrintSpeed(printerId, level);
            return;
        }

        agentGateway.setPrintSpeed(printerId, level);
    }

    @Override
    public void pushAll(UUID printerId) {
        if (isDirect(printerId)) {
            directGateway.pushAll(printerId);
            return;
        }

        agentGateway.pushAll(printerId);
    }

    @Override
    public void skipObjects(
            UUID printerId,
            List<Integer> objectIds
    ) {
        if (isDirect(printerId)) {
            directGateway.skipObjects(printerId, objectIds);
            return;
        }

        agentGateway.skipObjects(printerId, objectIds);
    }

    @Override
    public void spoolChange(
            UUID printerId,
            SpoolChangeRequestDto request
    ) {
        if (isDirect(printerId)) {
            directGateway.spoolChange(printerId, request);
            return;
        }

        agentGateway.spoolChange(printerId, request);
    }

    private boolean isDirect(UUID printerId) {
        return printerManagementService.getPrinter(printerId)
                .getConnectionMode() == PrinterConnectionMode.DIRECT;
    }
}
