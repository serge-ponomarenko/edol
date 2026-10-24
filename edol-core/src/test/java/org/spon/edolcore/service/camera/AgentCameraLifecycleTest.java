package org.spon.edolcore.service.camera;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.MqttMessagePublisher;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.agent.command.AgentCommandPublisher;
import org.spon.edolcore.service.agent.mqtt.AgentMqttService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.tenant.MissingCoreTenantContextException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentCameraLifecycleTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID printer = UUID.randomUUID();
    private final UUID firstAgent = UUID.randomUUID();
    private final UUID replacement = UUID.randomUUID();
    private final CoreTenantContext tenants = new CoreTenantContext();
    private final AgentMqttService authority = spy(new AgentMqttService(mock(JdbcTemplate.class), tenants,
            mock(org.spon.edolcore.service.agent.mqtt.AgentBroker.class),
            mock(org.spon.edolcore.service.agent.pairing.AgentCredentialCodec.class),
            mock(PlatformTransactionManager.class)));
    private final MqttMessagePublisher mqtt = mock(MqttMessagePublisher.class);
    private final PrinterConnectionConfigurationRepository configurations = mock(PrinterConnectionConfigurationRepository.class);
    private final PrinterStateService states = mock(PrinterStateService.class);
    private final PrinterState state = new PrinterState();

    private AgentCommandPublisher publisher() {
        ObjectProvider<AgentMqttService> provider = mock();
        when(provider.getObject()).thenReturn(authority);
        when(provider.getIfAvailable()).thenReturn(authority);
        when(states.getState(printer)).thenReturn(state);
        var result = new AgentCommandPublisher(mqtt, configurations, states, provider);
        ReflectionTestUtils.setField(result, "mode", "secure-multi-tenant");
        return result;
    }

    private void binding(UUID agent, UUID owner, boolean usable) {
        doReturn(List.of(new AgentMqttService.Binding(agent, owner, printer,
                usable ? "READY" : "REVOKED", usable))).when(authority).catalog();
    }

    @Test
    void activeRevokedUnpairedAndReplacementTransitionsDoNotPublishObsoleteSnapshotCommands() {
        var publisher = publisher();
        state.setOnline(true);
        try (var ignored = tenants.open(tenant)) {
            binding(firstAgent, tenant, true);
            publisher.publishSnapshotIfAvailable(printer, "snapshot");
            verify(mqtt).publish("edol/agent/v1/" + firstAgent + "/commands/agent", "snapshot");
            clearInvocations(mqtt);
            binding(firstAgent, tenant, false);
            for (int interval = 0; interval < 4; interval++) publisher.publishSnapshotIfAvailable(printer, "snapshot");
            doReturn(List.of()).when(authority).catalog();
            publisher.publishSnapshotIfAvailable(printer, "snapshot");
            verifyNoInteractions(mqtt);
            binding(replacement, tenant, true);
            state.setOnline(false);
            publisher.publishSnapshotIfAvailable(printer, "snapshot");
            verifyNoInteractions(mqtt);
            state.setOnline(true);
            publisher.publishSnapshotIfAvailable(printer, "snapshot");
            verify(mqtt).publish("edol/agent/v1/" + replacement + "/commands/agent", "snapshot");
        }
        verifyNoInteractions(configurations);
    }

    @Test
    void foreignAndMissingTenantNeverPublishAndOrdinaryCommandsStillFailClosed() {
        var publisher = publisher();
        state.setOnline(true);
        binding(firstAgent, UUID.randomUUID(), true);
        try (var ignored = tenants.open(tenant)) {
            publisher.publishSnapshotIfAvailable(printer, "snapshot");
            assertThatThrownBy(() -> publisher.publish(printer, "command")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> authority.availableCommandTopic(printer, "#"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> publisher.publishSnapshotIfAvailable(printer, "snapshot"))
                .isInstanceOf(MissingCoreTenantContextException.class);
        verifyNoInteractions(mqtt);
    }

    @Test
    void disabledSecureMqttComponentCannotPublishSnapshots() {
        ObjectProvider<AgentMqttService> absent = mock();
        var publisher = new AgentCommandPublisher(mqtt, configurations, states, absent);
        ReflectionTestUtils.setField(publisher, "mode", "secure-multi-tenant");
        publisher.publishSnapshotIfAvailable(printer, "snapshot");
        verifyNoInteractions(mqtt, configurations, states);
    }

    @Test
    void homeSnapshotContractDoesNotRequireSecureAuthorityOrOnlineState() {
        var publisher = publisher();
        ReflectionTestUtils.setField(publisher, "mode", "home");
        var configuration = new PrinterConnectionConfiguration();
        configuration.setAgentId("home-agent");
        when(configurations.findByPrinterId(printer)).thenReturn(Optional.of(configuration));
        publisher.publishSnapshotIfAvailable(printer, "snapshot");
        verify(mqtt).publish("edol/agents/home-agent/commands/agent", "snapshot");
        verify(authority, never()).catalog();
    }
}
