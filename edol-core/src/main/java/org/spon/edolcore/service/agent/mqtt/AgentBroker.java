package org.spon.edolcore.service.agent.mqtt;

import java.util.UUID;

public interface AgentBroker {
    void provision(UUID id, String password);
    void disable(UUID id);
}
