package org.spon.edolcore.service.printer.connectivity;

import java.util.UUID;

public interface PrinterConnectivityProvider {

    boolean isConnected(UUID printerId);

}