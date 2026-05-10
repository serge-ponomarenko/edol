package org.spon.edolcore.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrinterEvent {

    private PrinterEventType type;

    private String fileName;

    private Integer layer;

    private Integer progress;

    private Integer errorCode;

}