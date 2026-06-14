package org.spon.edolcore.event.model;

import java.nio.file.Path;
import java.util.UUID;

public record ModelAvailableEvent(
        UUID printerId,
        Path modelFile
) {
}
