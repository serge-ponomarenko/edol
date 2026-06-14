package org.spon.edolcore.service.agent.command;

import java.util.UUID;

public interface AgentCommandGateway {

    void uploadModel(UUID printerId, String fileName);

    void enableSnapshotScheduler(UUID printerId);

    void disableSnapshotScheduler(UUID printerId);

}
