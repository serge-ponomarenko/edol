package org.spon.edolcore.service.printer.command;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.agent.mqtt.AgentBroker;
import org.spon.edolcore.service.agent.mqtt.AgentMqttService;
import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentPrinterCommandAuthorityTest {
    @Test
    void routesOnlyThePersistedUsableBindingInTheCurrentTenant() {
        UUID tenant = UUID.randomUUID(), printer = UUID.randomUUID(), agent = UUID.randomUUID();
        var context = mock(CoreTenantContext.class);
        when(context.getCurrentTenantId()).thenReturn(tenant);
        var authority = spy(new AgentMqttService(mock(JdbcTemplate.class), context, mock(AgentBroker.class),
                mock(AgentCredentialCodec.class), mock(PlatformTransactionManager.class)));
        doReturn(List.of(new AgentMqttService.Binding(agent, tenant, printer, "READY", true)))
                .when(authority).catalog();
        var mqtt = mock(MqttMessagePublisher.class);
        ObjectProvider<AgentMqttService> provider = mock();
        when(provider.getObject()).thenReturn(authority);
        var legacy = mock(PrinterConnectionConfigurationRepository.class);
        var publisher = new AgentPrinterCommandPublisher(mqtt, legacy, provider);
        ReflectionTestUtils.setField(publisher, "mode", "secure-multi-tenant");
        publisher.publish(printer, "synthetic-command");
        verify(mqtt).publish("edol/agent/v1/" + agent + "/commands/printer", "synthetic-command");
        verifyNoInteractions(legacy);

        clearInvocations(mqtt);
        for (var binding : List.of(
                new AgentMqttService.Binding(agent, UUID.randomUUID(), printer, "READY", true),
                new AgentMqttService.Binding(agent, tenant, UUID.randomUUID(), "READY", true),
                new AgentMqttService.Binding(agent, tenant, printer, "REVOKED", false))) {
            doReturn(List.of(binding)).when(authority).catalog();
            assertThatThrownBy(() -> publisher.publish(printer, "synthetic-command"))
                    .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(mqtt, legacy);
    }
}
