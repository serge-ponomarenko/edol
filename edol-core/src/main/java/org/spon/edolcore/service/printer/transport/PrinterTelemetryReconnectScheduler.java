package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.PrinterService;
import org.spon.edolcore.service.printer.telemetry.DefaultPrinterTelemetryProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrinterTelemetryReconnectScheduler {

    private final DefaultPrinterTelemetryProvider telemetryProvider;
    private final PrinterService printerService;
    private final LogContextFactory logContextFactory;
    private final ExecutorService virtualThreadExecutor;

    @Scheduled(fixedDelay = 30000)
    public void reconnect() {
        for (Printer printer :
                printerService.getEnabledPrinters()) {

            UUID printerId = printer.getId();

            if (!telemetryProvider.isConnected(
                    printerId
            )) {
                virtualThreadExecutor.submit(() -> {
                    logContextFactory
                            .printer(
                                    log.atInfo(),
                                    printerId
                            )
                            .log(
                                    "Attempting to connect printer"
                            );

                    telemetryProvider.connect(
                            printerId
                    );
                });
            }
        }
    }

}
