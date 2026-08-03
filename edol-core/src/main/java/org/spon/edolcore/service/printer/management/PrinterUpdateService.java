package org.spon.edolcore.service.printer.management;

import org.spon.edolcore.controller.dto.printer.UpdatePrinterConnectionRequest;
import org.spon.edolcore.controller.dto.printer.UpdatePrinterRequest;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;

import java.util.UUID;

public interface PrinterUpdateService {

    Printer updatePrinter(UUID printerId, UpdatePrinterRequest request);

    PrinterConnectionConfiguration updateConnection(
            UUID printerId,
            UpdatePrinterConnectionRequest request
    );

}