package org.spon.edolhub.model.dto;

import java.util.UUID;

public record CorePrinterDto(
        UUID printerId,
        String displayId,
        String name,
        String description,
        String serialNumber,
        String model,
        String connectionMode,
        String cameraProvider,
        boolean enabled
) {

    public CorePrinterDto(UUID printerId, String displayId, String name, boolean enabled) {
        this(printerId, displayId, name, null, null, null, null, null, enabled);
    }
}
