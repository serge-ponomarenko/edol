package org.spon.edolcore.service.printer.command;

import org.spon.edolcore.model.dto.SpoolChangeRequestDto;

import java.util.List;

public interface PrinterCommandGateway {

    void pause();

    void resume();

    void stop();

    void pushAll();

    void skipObjects(List<Integer> objectIds);

    void spoolChange(SpoolChangeRequestDto request);
}
