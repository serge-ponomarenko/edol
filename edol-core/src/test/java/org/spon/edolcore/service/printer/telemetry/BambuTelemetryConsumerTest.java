package org.spon.edolcore.service.printer.telemetry;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.management.PrinterManagementService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BambuTelemetryConsumerTest {
    private final PrinterStateService states = mock(PrinterStateService.class);
    private final PrinterManagementService printers = mock(PrinterManagementService.class);
    private final PrinterConnectivityStateService connectivity = mock(PrinterConnectivityStateService.class);
    private final BambuTelemetryConsumer consumer = new BambuTelemetryConsumer(states, printers, new LogContextFactory(), connectivity);
    private final UUID printer = UUID.randomUUID();

    @Test
    void reportsConnectivityOnlyAfterThePrinterStateWasSuccessfullyUpdated() {
        Printer configuration = new Printer();
        configuration.setConnectionMode(PrinterConnectionMode.DIRECT);
        when(printers.getPrinter(printer)).thenReturn(configuration);
        consumer.consume(printer, "{\"print\":{\"gcode_state\":\"IDLE\"}}".getBytes(StandardCharsets.UTF_8));
        var order = inOrder(states, connectivity);
        order.verify(states).update(eq(printer), any());
        order.verify(connectivity).setConnected(printer);
    }

    @Test
    void failedPrinterLookupCannotTurnAnUnprocessedPacketIntoOnlineState() {
        when(printers.getPrinter(printer)).thenThrow(new IllegalStateException("Synthetic tenant failure"));
        when(states.getState(printer)).thenReturn(new PrinterState());
        consumer.consume(printer, "{\"print\":{}}".getBytes(StandardCharsets.UTF_8));
        verifyNoInteractions(connectivity);
        verify(states, never()).update(any(), any());
    }
}
