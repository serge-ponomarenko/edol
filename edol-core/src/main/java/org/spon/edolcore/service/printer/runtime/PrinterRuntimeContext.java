package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PrinterRuntimeContext {

    private final UUID printerId;

    private final PrinterStateRuntime printerStateRuntime =
            new PrinterStateRuntime();

    private final RecoveryRuntimeState recoveryRuntimeState =
            new RecoveryRuntimeState();

    private final MetadataRuntimeState metadataRuntimeState =
            new MetadataRuntimeState();

    private final CameraRuntimeState cameraRuntimeState =
            new CameraRuntimeState();

    public PrinterRuntimeContext(UUID printerId) {
        this.printerId = printerId;
        printerStateRuntime.getState().setPrinterId(printerId);
    }
}