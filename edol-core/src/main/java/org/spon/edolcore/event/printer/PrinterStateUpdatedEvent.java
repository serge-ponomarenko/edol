package org.spon.edolcore.event.printer;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
public class PrinterStateUpdatedEvent {
    @Getter
    private UUID printerId;
}
