package org.spon.edolcore.service.printer.management;

import java.util.UUID;

public interface PrinterDecommissionService {

    void decommissionPrinter(UUID printerId);

}
