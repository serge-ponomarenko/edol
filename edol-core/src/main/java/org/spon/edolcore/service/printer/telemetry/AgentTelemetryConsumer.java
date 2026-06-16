package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.AgentStateService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentTelemetryConsumer {

    private final BambuTelemetryConsumer bambuTelemetryConsumer;
    private final AgentStateService agentStateService;

    public void consume(
            UUID printerId,
            String payload
    ) {
        agentStateService.recordActivity(
                printerId
        );

        bambuTelemetryConsumer.consume(
                printerId,
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }
}