package org.spon.edolhub.model.dto;

import org.spon.edol.model.PrinterState;

import java.util.UUID;

public record PrinterOverviewDto(
        UUID id,
        String displayId,
        String name,
        String model,
        boolean enabled,
        boolean availableInCore,
        PrinterState state,
        Long jobId,
        int maintenanceAlertCount
) {
}
