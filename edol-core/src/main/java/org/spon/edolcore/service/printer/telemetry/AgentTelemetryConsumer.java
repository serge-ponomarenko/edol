package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AgentTelemetryConsumer {

    private final BambuTelemetryConsumer bambuTelemetryConsumer;

    public void consume(String payload) {
        bambuTelemetryConsumer.consume(
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
}