package org.spon.edolcore.service.printer.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.model.dto.SpoolChangeRequestDto;
import org.spon.edolcore.service.printer.command.payload.PrinterCommandPayloadFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "AGENT")
@RequiredArgsConstructor
public class AgentPrinterCommandGateway implements PrinterCommandGateway {

    private final AgentPrinterCommandPublisher publisher;

    @Override
    public void pause() {
        publisher.publish(
                PrinterCommandPayloadFactory.pause()
        );
    }

    @Override
    public void resume() {
        publisher.publish(
                PrinterCommandPayloadFactory.resume()
        );
    }

    @Override
    public void stop() {
        publisher.publish(
                PrinterCommandPayloadFactory.stop()
        );
    }

    @Override
    public void pushAll() {
        publisher.publish(
                PrinterCommandPayloadFactory.pushAll()
        );
    }

    @Override
    public void skipObjects(List<Integer> objectIds) {
        publisher.publish(
                PrinterCommandPayloadFactory.skipObjects(objectIds)
        );
    }

    @Override
    public void spoolChange(SpoolChangeRequestDto request) {
        publisher.publish(
                PrinterCommandPayloadFactory.spoolChange(request)
        );
    }
}
