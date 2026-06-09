package org.spon.edolcore.service.printer.connectivity;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrinterConnectivityStateService {

    private final PrinterStateService printerStateService;

    public void setConnected() {
        if (printerStateService.getState().isOnline()) {
            return;
        }

        printerStateService.getState().setOnline(true);
        printerStateService.publish(PrinterEventType.PRINTER_ONLINE);
    }

    public void setDisconnected() {
        if (!printerStateService.getState().isOnline()) {
            return;
        }

        printerStateService.getState().setOnline(false);
        printerStateService.publish(PrinterEventType.PRINTER_OFFLINE);
    }
}