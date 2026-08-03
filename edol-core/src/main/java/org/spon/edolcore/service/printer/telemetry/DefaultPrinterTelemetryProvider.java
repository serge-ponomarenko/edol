package org.spon.edolcore.service.printer.telemetry;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterTelemetryProvider
        implements PrinterTelemetryProvider {

    private final PrinterManagementService printerManagementService;
    private final DirectPrinterTelemetryProvider directProvider;
    private final AgentPrinterTelemetryProvider agentProvider;

    @Override
    public void connect(UUID printerId) {
        provider(printerId).connect(printerId);
    }

    @Override
    public boolean isConnected(UUID printerId) {
        return provider(printerId)
                .isConnected(printerId);
    }

    private PrinterTelemetryProvider provider(
            UUID printerId
    ) {
        Printer printer =
                printerManagementService.getPrinter(printerId);

        return switch (
                printer.getConnectionMode()
                ) {
            case DIRECT -> directProvider;
            case AGENT -> agentProvider;
        };
    }
}