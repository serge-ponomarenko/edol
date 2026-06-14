package org.spon.edolcore.service.printer.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.PrinterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BambuTelemetryConsumer {

    private final PrinterStateService stateService;
    private final PrinterService printerService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${bambu.show-raw-mqtt}")
    private boolean showRawMqttMessages;

    public void consume(
            UUID printerId,
            byte[] payload
    ) {
        try {
            JsonNode root = mapper.readTree(payload);

            PrinterConnectionMode connectionMode = printerService
                    .getPrinter(printerId)
                    .getConnectionMode();

            String connection = PrinterConnectionMode.AGENT.equals(connectionMode) ? "A" : "D";  // D - direct connection, A - agent connectio

            if (showRawMqttMessages) {
                log.atInfo()
                        .addKeyValue("printerId", printerId)
                        .log("[{}] {}", connection, root);
            }

            if (root.has("print")) {
                stateService.update(
                        printerId,
                        root.get("print")
                );
            }

        } catch (Exception e) {
            log.atError()
                    .addKeyValue("printerId", printerId)
                    .log("Cannot process printer telemetry", e);
        }
    }
}