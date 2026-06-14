package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataRecoveryService {

    private final ApplicationEventPublisher events;
    private final LogContextFactory logContextFactory;
    private final PrinterStateService printerStateService;

    public void recoverMetadata(UUID printerId, String gcodeFile) {
        Path model = Path.of("models", printerId.toString(), gcodeFile);
        PrinterState state = printerStateService.getState(printerId);

        if (!Files.exists(model)) {
            logContextFactory
                    .session(
                            log.atWarn(),
                            printerId,
                            state.getSessionId()
                    )
                    .log(
                            "Cached model not found for recovery: {}",
                            model
                    );

            return;
        }

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        state.getSessionId()
                )
                .log(
                        "Recovering metadata from cached model {}",
                        model.getFileName()
                );

        events.publishEvent(
                new ModelAvailableEvent(printerId, model)
        );
    }
}
