package org.spon.edolcore.service.agent.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.command.payload.AgentCommandPayloadFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAgentCommandGateway implements AgentCommandGateway {

    private final AgentCommandPublisher publisher;

    @Override
    public void uploadModel(String fileName) {
        publisher.publish(
                AgentCommandPayloadFactory.uploadModel(fileName)
        );
    }

    @Override
    public void enableSnapshotScheduler() {
        publisher.publish(
                AgentCommandPayloadFactory.enableSnapshotScheduler()
        );
    }

    @Override
    public void disableSnapshotScheduler() {
        publisher.publish(
                AgentCommandPayloadFactory.disableSnapshotScheduler()
        );
    }

}
