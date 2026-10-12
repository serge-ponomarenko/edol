package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.spon.edolams.config.Stage8TerminalSimulatorProperties;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Stage8TerminalOperationalSimulatorTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");

    @Test
    void returnsDeterministicStateAndReflectsSafeSlotAssignment() {
        Stage8TerminalOperationalSimulator simulator = simulator();
        var terminal = terminal(TENANT_ID, PRINTER_ID);

        var initial = simulator.state(terminal);

        assertThat(initial.getTemperature()).isEqualTo(23.5);
        assertThat(initial.getHumidityRaw()).isEqualTo(35);
        assertThat(initial.getSlots()).hasSize(4);
        assertThat(initial.getSlots().get(0).getSpoolId()).isEqualTo(800001L);
        assertThat(initial.getSlots().get(1).isEmpty()).isTrue();
        assertThat(initial.getSlots().get(2).getSpoolId()).isEqualTo(800002L);

        assertThat(simulator.setSpool(terminal, 800002L, 1).getStatusCode().value()).isEqualTo(200);

        var updated = simulator.state(terminal);
        assertThat(updated.getActiveSlot()).isEqualTo(1);
        assertThat(updated.getSlots().get(1).getSpoolId()).isEqualTo(800002L);
        assertThat(updated.getSlots().get(1).isActive()).isTrue();

        assertThat(simulator.setSpool(terminal, 800001L, 254).getStatusCode().value()).isEqualTo(200);
        assertThat(simulator.state(terminal).getExtTray().getSpoolId()).isEqualTo(800001L);
        assertThat(simulator.state(terminal).getExtTray().isActive()).isTrue();
    }

    @Test
    void resolvesOnlyFixtureSpoolsAndRejectsInvalidAssignments() {
        Stage8TerminalOperationalSimulator simulator = simulator();
        var terminal = terminal(TENANT_ID, PRINTER_ID);

        assertThat(simulator.findSpool(terminal, 800001L).getBody().getMaterial()).isEqualTo("PLA");
        assertThat(simulator.findSpool(terminal, 900000L).getStatusCode().value()).isEqualTo(404);
        assertThat(simulator.setSpool(terminal, 900000L, 0).getStatusCode().value()).isEqualTo(404);

        assertThatThrownBy(() -> simulator.setSpool(terminal, 800001L, 255))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void deniesATerminalOutsideTheFixtureTenantOrPrinter() {
        Stage8TerminalOperationalSimulator simulator = simulator();

        assertThatThrownBy(() -> simulator.state(terminal(UUID.randomUUID(), PRINTER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
        assertThatThrownBy(() -> simulator.findSpool(terminal(TENANT_ID, UUID.randomUUID()), 800001L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    private Stage8TerminalOperationalSimulator simulator() {
        Stage8TerminalOperationalSimulator simulator = new Stage8TerminalOperationalSimulator(
                new Stage8TerminalSimulatorProperties(true, TENANT_ID, PRINTER_ID)
        );
        simulator.initializeFixture();
        return simulator;
    }

    private TerminalAuthenticationService.AuthenticatedTerminal terminal(UUID tenantId, UUID printerId) {
        return new TerminalAuthenticationService.AuthenticatedTerminal(
                UUID.randomUUID(), tenantId, printerId, null, 1
        );
    }
}
