package org.spon.edol.model;

import lombok.Data;

import java.util.List;

@Data
public class PrinterState {

    private int printerId = 1;
    private boolean isOnline;

    private String gcodeState;

    private int progress;
    private int layer;
    private int totalLayers;
    private int remainingTime;
    private int plateIndex;

    private double nozzleTemp;
    private double nozzleTargetTemp;

    private int speed;

    private double bedTemp;
    private double bedTargetTemp;

    private String currentFile;
    private String currentTask;

    private String wifiSignal;

    private AmsState ams = new AmsState();

    private boolean externalSpoolUsed;
    private ExtTray extTray = new ExtTray();

    private List<Filament> filaments;

    private boolean printing;

    private String sessionId;

    private List<PrintObject> printObjects;

    private PrinterError error;

    private List<Integer> amsMapping;

}
