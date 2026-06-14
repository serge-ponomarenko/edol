package org.spon.edolcore.service.printer.command;

import org.spon.edolcore.model.dto.SpoolChangeRequestDto;

import java.util.List;
import java.util.UUID;

public interface AgentPrinterCommandGateway {

    void pause(UUID printerId);

    void resume(UUID printerId);

    void stop(UUID printerId);

    void pushAll(UUID printerId);

    void skipObjects(UUID printerId, List<Integer> objectIds);

    void spoolChange(UUID printerId, SpoolChangeRequestDto request);
}