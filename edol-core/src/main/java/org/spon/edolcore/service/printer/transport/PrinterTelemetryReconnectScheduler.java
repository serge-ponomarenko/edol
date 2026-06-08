package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.printer.telemetry.PrinterTelemetryProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrinterTelemetryReconnectScheduler {

    private final PrinterTelemetryProvider telemetryProvider;

    @Scheduled(fixedDelay = 30000)
    public void reconnect() {
        if (!telemetryProvider.isConnected()) {
            log.info("Attempting to connect to printer...");
            telemetryProvider.connect();
        }
    }

}
