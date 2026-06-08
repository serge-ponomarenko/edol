package org.spon.edolcore.event.model;

import java.nio.file.Path;

public record ModelAvailableEvent(
        Path modelFile
) {
}
