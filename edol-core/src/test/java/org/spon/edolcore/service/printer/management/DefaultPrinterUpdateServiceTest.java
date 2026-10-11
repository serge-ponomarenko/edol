package org.spon.edolcore.service.printer.management;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;
import org.spon.edolcore.controller.dto.printer.UpdatePrinterConnectionRequest;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPrinterUpdateServiceTest {

    @Test
    void recreatesMissingConnectionConfigurationForAnExistingPrinter() {
        UUID printerId = UUID.randomUUID();
        Printer printer = Printer.builder().id(printerId).build();
        PrinterRepository printerRepository = mock(PrinterRepository.class);
        PrinterConnectionConfigurationRepository configurationRepository = mock(PrinterConnectionConfigurationRepository.class);
        PrinterRuntimeLifecycleService runtimeLifecycleService = mock(PrinterRuntimeLifecycleService.class);
        DefaultPrinterUpdateService service = new DefaultPrinterUpdateService(
                printerRepository,
                configurationRepository,
                new PrinterMapper(),
                runtimeLifecycleService,
                new LogContextFactory()
        );
        UpdatePrinterConnectionRequest request = new UpdatePrinterConnectionRequest(
                "mqtt.example.test", 1883, null, null, null, "access-code", "agent-1"
        );
        when(printerRepository.findById(printerId)).thenReturn(Optional.of(printer));
        when(configurationRepository.findByPrinterId(printerId)).thenReturn(Optional.empty());
        when(configurationRepository.save(any(PrinterConnectionConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrinterConnectionConfiguration result = service.updateConnection(printerId, request);

        assertThat(result.getPrinter()).isSameAs(printer);
        assertThat(result.getMqttHost()).isEqualTo("mqtt.example.test");
        assertThat(result.getMqttPort()).isEqualTo(1883);
        assertThat(result.getAccessCode()).isEqualTo("access-code");
        assertThat(result.getAgentId()).isEqualTo("agent-1");
        verify(configurationRepository).save(result);
        verify(runtimeLifecycleService).restartRuntime(printerId);
    }
}
