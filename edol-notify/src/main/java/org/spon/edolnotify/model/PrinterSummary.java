package org.spon.edolnotify.model;

import java.util.UUID;

public record PrinterSummary(UUID printerId, String displayId, String name, boolean enabled) {
}
