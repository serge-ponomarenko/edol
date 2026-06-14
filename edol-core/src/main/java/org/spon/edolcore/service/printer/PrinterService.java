package org.spon.edolcore.service.printer;

import org.spon.edolcore.persistence.printer.Printer;

import java.util.List;
import java.util.UUID;

public interface PrinterService {

    Printer getDefaultPrinter();

    Printer getPrinter(UUID printerId);

    List<Printer> getEnabledPrinters();
}