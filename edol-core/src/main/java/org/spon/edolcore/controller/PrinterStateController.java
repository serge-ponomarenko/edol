package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/printers")
@RequiredArgsConstructor
@Slf4j
public class PrinterStateController {

    private final PrinterConnectivityProvider connectivityProvider;
    private final PrinterStateService printerStateService;
    private final PrinterManagementService printerManagementService;
    private final DeploymentMode deploymentMode;

    @GetMapping("/state")
    public List<PrinterState> getAllStates() {
        if (deploymentMode == DeploymentMode.HOME) {
            return printerStateService.getAllStates();
        }
        Set<UUID> visiblePrinters = printerManagementService.getPrinters().stream()
                .map(printer -> printer.getId())
                .collect(Collectors.toSet());
        return printerStateService.getAllStates().stream()
                .filter(state -> visiblePrinters.contains(state.getPrinterId()))
                .toList();
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
