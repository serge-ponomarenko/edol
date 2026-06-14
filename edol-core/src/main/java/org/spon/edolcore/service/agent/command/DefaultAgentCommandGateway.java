package org.spon.edolcore.service.agent.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.command.payload.AgentCommandPayloadFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAgentCommandGateway implements AgentCommandGateway {

    private final AgentCommandPublisher publisher;

    @Override
    public void uploadModel(UUID printerId, String fileName) {
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.uploadModel(fileName)
        );
    }

    @Override
    public void enableSnapshotScheduler(UUID printerId) {
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.enableSnapshotScheduler()
        );
    }

    @Override
    public void disableSnapshotScheduler(UUID printerId) {
        publisher.publish(
                printerId,
                AgentCommandPayloadFactory.disableSnapshotScheduler()
        );
    }

}
