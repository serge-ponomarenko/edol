package org.spon.edolcore.event.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataRecoveryService {

    private final ApplicationEventPublisher events;

    public void recoverMetadata(String gcodeFile) {
        Path model = Path.of("models", gcodeFile);

        if (!Files.exists(model)) {
            log.warn(
                    "Cached model not found for recovery: {}",
                    model
            );
            return;
        }

        log.info(
                "Recovering metadata from cached model {}",
                model.getFileName()
        );

        events.publishEvent(
                new ModelAvailableEvent(model)
        );
    }
}
