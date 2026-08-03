package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrinterRuntimeBootstrap {

    private final PrinterRepository printerRepository;
    private final PrinterRuntimeLifecycleService runtimeLifecycleService;
    private final LogContextFactory logContextFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        printerRepository.findByEnabledTrue()
                .forEach(printer -> {
                    runtimeLifecycleService.createRuntime(
                            printer.getId()
                    );

                    runtimeLifecycleService.startRuntime(
                            printer.getId()
                    );

                    logContextFactory
                            .printer(
                                    log.atInfo(),
                                    printer.getId()
                            )
                            .log(
                                    "Printer runtime initialized: {}", printer.getDisplayId()
                            );
                });
    }
}