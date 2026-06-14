package org.spon.edolcore.service.printer.command;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.model.dto.SpoolChangeRequestDto;
import org.spon.edolcore.service.printer.command.payload.PrinterCommandPayloadFactory;
import org.spon.edolcore.service.printer.transport.BambuMqttCommandPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectPrinterCommandGatewayImpl implements DirectPrinterCommandGateway {

    private final BambuMqttCommandPublisher publisher;

    @Override
    public void pause(UUID printerId) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.pause()
        );
    }

    @Override
    public void resume(UUID printerId) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.resume()
        );
    }

    @Override
    public void stop(UUID printerId) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.stop()
        );
    }

    @Override
    public void pushAll(UUID printerId) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.pushAll()
        );
    }

    @Override
    public void skipObjects(UUID printerId, List<Integer> objectIds) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.skipObjects(objectIds)
        );
    }

    @Override
    public void spoolChange(UUID printerId, SpoolChangeRequestDto request) {
        publisher.publish(
                printerId,
                PrinterCommandPayloadFactory.spoolChange(request)
        );
    }
}
