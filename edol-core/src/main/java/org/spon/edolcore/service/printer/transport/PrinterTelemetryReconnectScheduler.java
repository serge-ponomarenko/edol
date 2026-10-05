package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeQueryService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.spon.edolcore.service.printer.telemetry.DefaultPrinterTelemetryProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "edol-core.runtime.printer-runtime-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PrinterTelemetryReconnectScheduler {

    private final DefaultPrinterTelemetryProvider telemetryProvider;
    private final PrinterRuntimeQueryService runtimeQueryService;
    private final LogContextFactory logContextFactory;
    private final ExecutorService virtualThreadExecutor;
    private final CoreRuntimeTenantExecutor runtimeTenantExecutor;

    @Scheduled(fixedDelay = 30000)
    public void reconnect() {
        for (UUID printerId :
                runtimeQueryService.getActivePrinterIds()) {
            if (!telemetryProvider.isConnected(
                    printerId
            )) {
                virtualThreadExecutor.submit(() ->
                    runtimeTenantExecutor.execute(printerId, () -> {
                        logContextFactory
                                .printer(
                                        log.atInfo(),
                                        printerId
                                )
                                .log(
                                        "Attempting to connect printer"
                                );

                        telemetryProvider.connect(printerId);
                    })
                );
            }
        }
    }

}
