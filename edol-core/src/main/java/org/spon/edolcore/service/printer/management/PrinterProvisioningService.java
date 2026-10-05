package org.spon.edolcore.service.printer.management;

import org.spon.edolcore.controller.dto.printer.CreatePrinterRequest;
import org.spon.edolcore.persistence.printer.Printer;

import java.util.UUID;

public interface PrinterProvisioningService {

    Printer createPrinter(CreatePrinterRequest request, UUID idempotencyKey);

}
