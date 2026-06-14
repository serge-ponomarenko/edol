package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentTelemetryConsumer {

    private final BambuTelemetryConsumer bambuTelemetryConsumer;

    public void consume(
            UUID printerId,
            String payload
    ) {
        bambuTelemetryConsumer.consume(
                printerId,
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
}