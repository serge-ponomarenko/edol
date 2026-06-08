package org.spon.edolcore.service.printer.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BambuTelemetryConsumer {

    private final PrinterStateService stateService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${bambu.show-raw-mqtt}")
    private boolean showRawMqttMessages;

    @Value("${edol.printer.connection-mode}")
    private String connectionMode;

    public void consume(byte[] payload) {
        try {
            JsonNode root = mapper.readTree(payload);

            String source = "AGENT".equalsIgnoreCase(connectionMode) ? "A" : "D";  // D - direct connection, A - agent connectio

            if (showRawMqttMessages) {
                log.info("[{}] {}", source, root);
            }

            if (root.has("print")) {
                stateService.update(root.get("print"));
            }

        } catch (Exception e) {
            log.error("Cannot process printer telemetry", e);
        }
    }
}