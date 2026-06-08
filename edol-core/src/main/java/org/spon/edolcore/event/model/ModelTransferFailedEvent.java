package org.spon.edolcore.event.model;

public record ModelTransferFailedEvent(
        String fileName,
        String reason
) {
}
