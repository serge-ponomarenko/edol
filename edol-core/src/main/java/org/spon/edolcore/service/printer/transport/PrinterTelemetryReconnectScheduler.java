package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.printer.PrinterService;
import org.spon.edolcore.service.printer.telemetry.DefaultPrinterTelemetryProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrinterTelemetryReconnectScheduler {

    private final DefaultPrinterTelemetryProvider telemetryProvider;
    private final PrinterService printerService;

    @Scheduled(fixedDelay = 30000)
    public void reconnect() {
        for (Printer printer :
                printerService.getEnabledPrinters()) {

            UUID printerId = printer.getId();

            if (!telemetryProvider.isConnected(
                    printerId
            )) {

                log.info(
                        "Attempting to connect printer {}",
                        printerId
                );

                telemetryProvider.connect(
                        printerId
                );
            }
        }
    }

}
