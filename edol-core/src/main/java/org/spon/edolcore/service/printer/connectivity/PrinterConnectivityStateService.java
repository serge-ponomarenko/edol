package org.spon.edolcore.service.printer.connectivity;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrinterConnectivityStateService {

    private final PrinterStateService printerStateService;

    public void setConnected(UUID printerId) {
        if (printerStateService.getState(printerId).isOnline()) {
            return;
        }

        printerStateService.getState(printerId).setOnline(true);
        printerStateService.publish(
                printerId, PrinterEventType.PRINTER_ONLINE
        );
    }

    public void setDisconnected(UUID printerId) {
        if (!printerStateService.getState(printerId).isOnline()) {
            return;
        }

        printerStateService.getState(printerId).setOnline(false);
        printerStateService.publish(
                printerId,
                PrinterEventType.PRINTER_OFFLINE
        );
    }
}