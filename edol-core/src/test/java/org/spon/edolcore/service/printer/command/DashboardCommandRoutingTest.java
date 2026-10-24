package org.spon.edolcore.service.printer.command;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.printer.transport.BambuMqttCommandPublisher;
import org.spon.edolcore.service.printer.command.payload.PrinterCommandPayloadFactory;

import java.util.UUID;

import static org.mockito.Mockito.*;

class DashboardCommandRoutingTest {
    @Test
    void directPauseAndResumeReachOnlyBambuTransport() {
        var printers = mock(PrinterManagementService.class);
        var bambu = mock(BambuMqttCommandPublisher.class);
        var agent = mock(AgentPrinterCommandPublisher.class);
        UUID id = configure(printers, PrinterConnectionMode.DIRECT);
        var gateway = gateway(printers, bambu, agent);
        gateway.pause(id);
        gateway.resume(id);
        verify(bambu).publish(id, PrinterCommandPayloadFactory.pause());
        verify(bambu).publish(id, PrinterCommandPayloadFactory.resume());
        verifyNoInteractions(agent);
    }

    @Test
    void agentPauseAndResumeReachOnlyAgentTransport() {
        var printers = mock(PrinterManagementService.class);
        var bambu = mock(BambuMqttCommandPublisher.class);
        var agent = mock(AgentPrinterCommandPublisher.class);
        UUID id = configure(printers, PrinterConnectionMode.AGENT);
        var gateway = gateway(printers, bambu, agent);
        gateway.pause(id);
        gateway.resume(id);
        verify(agent).publish(id, PrinterCommandPayloadFactory.pause());
        verify(agent).publish(id, PrinterCommandPayloadFactory.resume());
        verifyNoInteractions(bambu);
    }

    @Test
    void unavailablePrinterCannotFallBackToAnotherTransport() {
        var printers = mock(PrinterManagementService.class);
        var bambu = mock(BambuMqttCommandPublisher.class);
        var agent = mock(AgentPrinterCommandPublisher.class);
        UUID id = UUID.randomUUID();
        when(printers.getPrinter(id)).thenThrow(new IllegalArgumentException("Unavailable"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gateway(printers, bambu, agent).pause(id))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(bambu, agent);
    }

    private DefaultPrinterCommandGateway gateway(PrinterManagementService printers,
            BambuMqttCommandPublisher bambu, AgentPrinterCommandPublisher agent) {
        return new DefaultPrinterCommandGateway(printers,
                new DirectPrinterCommandGatewayImpl(bambu), new AgentPrinterCommandGatewayImpl(agent));
    }

    private UUID configure(PrinterManagementService service, PrinterConnectionMode mode) {
        UUID id = UUID.randomUUID();
        var printer = new Printer();
        printer.setConnectionMode(mode);
        when(service.getPrinter(id)).thenReturn(printer);
        return id;
    }
}
