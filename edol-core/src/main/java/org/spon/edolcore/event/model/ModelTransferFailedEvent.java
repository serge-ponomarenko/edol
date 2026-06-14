package org.spon.edolcore.event.model;

import java.util.UUID;

public record ModelTransferFailedEvent(
        UUID printerId,
        String fileName,
        String reason
) {
}
