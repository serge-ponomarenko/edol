package org.spon.edolcore.service.agent.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.command.payload.AgentCommandPayloadFactory;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAgentCommandGateway implements AgentCommandGateway {

    private final AgentCommandPublisher publisher;
    private final PrinterManagementService printers;

    @Override
    public void uploadModel(UUID printerId, String fileName) {
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.uploadModel(fileName)
        );
    }

    @Override
    public void enableSnapshotScheduler(UUID printerId) {
        if (!usesAgent(printerId)) return;
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.enableSnapshotScheduler()
        );
    }

    @Override
    public void disableSnapshotScheduler(UUID printerId) {
        if (!usesAgent(printerId)) return;
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.disableSnapshotScheduler()
        );
    }

    @Override
    public void uploadSnapshot(UUID printerId) {
        if (!usesAgent(printerId)) return;
        publisher.publishSnapshotIfAvailable(printerId, AgentCommandPayloadFactory.UPLOAD_SNAPSHOT);
    }

    private boolean usesAgent(UUID printerId) {
        return printers.getPrinter(printerId).getConnectionMode() == PrinterConnectionMode.AGENT;
    }

}
