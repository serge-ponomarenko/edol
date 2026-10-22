package org.spon.edolcore.agent;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.controller.AgentPairingManagementController;
import org.spon.edolcore.service.agent.mqtt.AgentMqttService;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentMqttManagementTest {
    @Test
    void replacementWaitsForBrokerRetirementAndDoesNotRetireAConcurrentNewPairing() {
        var agents = mock(AgentPairingService.class);
        var mqtt = mock(AgentMqttService.class);
        ObjectProvider<AgentMqttService> provider = mock();
        when(provider.getIfAvailable()).thenReturn(mqtt);
        when(provider.getObject()).thenReturn(mqtt);
        var controller = new AgentPairingManagementController(agents, provider);
        UUID printer = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(mqtt).flushRevocations(printer);
        assertThatThrownBy(() -> controller.rotate(printer)).isInstanceOf(ResponseStatusException.class);
        verify(agents).revoke(printer);
        verify(agents, never()).create(any(), anyBoolean());
        assertThatThrownBy(() -> controller.create(printer)).isInstanceOf(ResponseStatusException.class);
        verify(agents, never()).create(any(), anyBoolean());
        doNothing().when(mqtt).flushRevocations(printer);
        controller.rotate(printer);
        verify(agents).create(printer, false);
        verify(agents, never()).create(printer, true);
    }

    @Test
    void disabledMqttComponentCannotReportPendingRevocationAsCompleteOrIssueAnotherCode() {
        var agents = mock(AgentPairingService.class);
        ObjectProvider<AgentMqttService> absentBroker = mock();
        var controller = new AgentPairingManagementController(agents, absentBroker);
        UUID printer = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(agents).requireMqttRetired(printer);
        assertThatThrownBy(() -> controller.revoke(printer)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.rotate(printer)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.create(printer)).isInstanceOf(ResponseStatusException.class);
        verify(agents, times(2)).revoke(printer);
        verify(agents, never()).create(any(), anyBoolean());
        doNothing().when(agents).requireMqttRetired(printer);
        controller.create(printer);
        verify(agents).create(printer, false);
    }
}
