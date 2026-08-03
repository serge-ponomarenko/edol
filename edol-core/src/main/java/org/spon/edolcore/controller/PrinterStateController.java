package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/printers")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final DefaultPrinterResolver defaultPrinterResolver;
    private final PrinterConnectivityProvider connectivityProvider;
    private final PrinterStateService printerStateService;

    @GetMapping("/state-default")
    @Deprecated(forRemoval = true)
    public PrinterState getStateDefault() {
        return getState(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping("/state")
    public List<PrinterState> getAllStates() {
        return printerStateService.getAllStates();
    }

    @GetMapping("/{printerId}/state")
    public PrinterState getState(
            @PathVariable UUID printerId
    ) {
        if (connectivityProvider.isConnected(printerId)) {
            return printerStateService.getState(printerId);
        }

        PrinterState printerState = new PrinterState();
        printerState.setOnline(false);

        return printerState;
    }

}
