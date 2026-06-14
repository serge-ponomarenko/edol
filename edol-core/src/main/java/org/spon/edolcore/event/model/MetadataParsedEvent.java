package org.spon.edolcore.event.model;

import java.util.UUID;

public record MetadataParsedEvent(
        UUID printerId,
        String filename
) {
}
