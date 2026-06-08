package org.spon.edolcore.service.agent.command;

public interface AgentCommandGateway {

    void uploadModel(String fileName);

    void enableSnapshotScheduler();

    void disableSnapshotScheduler();

}
