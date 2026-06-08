package org.spon.edolcore.model.dto;

import lombok.Data;
import org.spon.edolcore.service.agent.event.AgentEventType;

@Data
public class AgentEventDto {

    private AgentEventType type;
    private String fileName;
    private String reason;

}