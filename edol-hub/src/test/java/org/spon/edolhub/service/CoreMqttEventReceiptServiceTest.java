package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.PrinterRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CoreMqttEventReceiptServiceTest {
    @Test
    void missingOrForeignPrinterFailsBeforeReceiptOrMutation() {
        UUID printerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CoreMqttEventEnvelope envelope = mock(CoreMqttEventEnvelope.class);
        when(envelope.printerId()).thenReturn(printerId);
        PrinterRepository printers = mock(PrinterRepository.class);
        JdbcClient jdbc = mock(JdbcClient.class);
        Runnable mutation = mock(Runnable.class);
        CoreMqttEventReceiptService service = new CoreMqttEventReceiptService(jdbc, printers);

        when(printers.findById(printerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.process(envelope, mutation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Hub printer");

        Printer foreign = new Printer();
        foreign.setTenantId(UUID.randomUUID());
        when(printers.findById(printerId)).thenReturn(Optional.of(foreign));
        when(envelope.tenantId()).thenReturn(tenantId);
        assertThatThrownBy(() -> service.process(envelope, mutation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not own Hub printer");
        verifyNoInteractions(jdbc, mutation);
    }
}
