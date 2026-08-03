package org.spon.edolcore.service.printer;

import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;

import java.util.List;
import java.util.UUID;

public interface PrinterManagementService {

    Printer getDefaultPrinter();

    Printer getPrinter(UUID printerId);

    PrinterConnectionConfiguration getConnection(UUID printerId);

    List<Printer> getEnabledPrinters();

    List<Printer> getPrinters();

}