package org.spon.edolcore.service.printer.management;

import org.spon.edolcore.controller.dto.printer.CreatePrinterRequest;
import org.spon.edolcore.persistence.printer.Printer;

public interface PrinterProvisioningService {

    Printer createPrinter(CreatePrinterRequest request);

}
