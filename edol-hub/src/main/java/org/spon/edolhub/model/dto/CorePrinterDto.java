package org.spon.edolhub.model.dto;

import java.util.UUID;

public record CorePrinterDto(
        UUID printerId,
        String displayId,
        String name,
        boolean enabled
) {
}
