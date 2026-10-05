package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.LogContextFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "edol-core.runtime.printer-runtime-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PrinterRuntimeBootstrap {

    private final CoreRuntimeCatalogEnumerator runtimeCatalogEnumerator;
    private final CoreRuntimeTenantExecutor runtimeTenantExecutor;
    private final PrinterRuntimeLifecycleService runtimeLifecycleService;
    private final LogContextFactory logContextFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        runtimeCatalogEnumerator.enabledPrinters()
                .forEach(entry -> runtimeTenantExecutor.execute(entry, () -> {
                    runtimeLifecycleService.reconcileRuntime(
                            entry.printerId()
                    );

                    logContextFactory
                            .printer(
                                    log.atInfo(),
                                    entry.printerId()
                            )
                            .log("Printer runtime initialized");
                }));
    }
}
