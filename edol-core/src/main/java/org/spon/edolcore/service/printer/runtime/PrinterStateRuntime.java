package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;
import lombok.Setter;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.PrinterState;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PrinterStateRuntime {

    private final PrinterState state = new PrinterState();

    private String lastState;

    private int lastError = 0;
    private int lastAmsStatus = -1;
    private int lastActiveSlot = -1;

    private int lastProgress = -1;
    private int lastLayer = -1;

    private List<AmsSlot> previousAmsSlots =
            new ArrayList<>();

    private int lastLogProgressMilestone = -1;
    private int lastLogLayerMilestone = -1;
}