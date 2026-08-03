package org.spon.edolcore.controller.dto.printer;

import org.spon.edolcore.persistence.printer.PrinterCameraProvider;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.persistence.printer.PrinterModel;

public record CreatePrinterRequest(
        String displayId,
        String name,
        String description,
        String serialNumber,
        PrinterModel model,
        PrinterConnectionMode connectionMode,
        PrinterCameraProvider cameraProvider,
        boolean enabled,
        PrinterConnectionDto connection
) {
}