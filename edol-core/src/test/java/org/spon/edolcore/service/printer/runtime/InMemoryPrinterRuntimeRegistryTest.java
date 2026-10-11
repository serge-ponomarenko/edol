package org.spon.edolcore.service.printer.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPrinterRuntimeRegistryTest {

    @Test
    void identifiesAnUnavailableRuntimeExplicitly() {
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        InMemoryPrinterRuntimeRegistry registry = new InMemoryPrinterRuntimeRegistry();

        assertThatThrownBy(() -> registry.get(printerId))
                .isInstanceOf(PrinterRuntimeNotFoundException.class)
                .hasMessage("Runtime not found for printer: " + printerId);
    }
}
