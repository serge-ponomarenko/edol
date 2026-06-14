package org.spon.edolcore.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PrinterEvent {

    private UUID printerId;

    private PrinterEventType type;

}