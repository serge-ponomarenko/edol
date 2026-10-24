package org.spon.edolcore.service.agent.mqtt;

import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.agent.pairing.AgentPairingService.Identity;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

public class AgentMqttService {
    private final JdbcTemplate jdbc;
    private final CoreTenantContext tenants;
    private final AgentBroker broker;
    private final AgentCredentialCodec codec;
    private final TransactionTemplate transactions;

    public AgentMqttService(JdbcTemplate jdbc, CoreTenantContext tenants, AgentBroker broker,
                            AgentCredentialCodec codec, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.broker = broker;
        this.codec = codec;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Credentials provision(Identity identity) {
        // Commit intent before any external side effect; a crash leaves a discoverable tombstone.
        transactions.executeWithoutResult(status -> {
            setTenant();
            lockActive(identity);
            if (jdbc.update("""
                    insert into core.agent_mqtt_authorities(agent_id, tenant_id, printer_id, state)
                    values (?, ?, ?, 'PROVISIONING') on conflict do nothing
                    """, identity.agentId(), identity.tenantId(), identity.printerId()) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MQTT authority already issued; replace agent");
            }
        });
        String password = codec.newSecret();
        try {
            transactions.executeWithoutResult(status -> {
                setTenant();
                lockActive(identity);
                String state = jdbc.queryForObject("""
                        select state from core.agent_mqtt_authorities where agent_id = ? for update
                        """, String.class, identity.agentId());
                if (!"PROVISIONING".equals(state)) throw unavailable();
                broker.provision(identity.agentId(), password);
                jdbc.update("update core.agent_mqtt_authorities set state = 'READY' where agent_id = ?", identity.agentId());
            });
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> {
                setTenant();
                jdbc.update("""
                    update core.agent_mqtt_authorities set state = 'REVOKE_PENDING' where agent_id = ?
                    """, identity.agentId());
            });
            throw unavailable();
        }
        return new Credentials(password);
    }

    private void setTenant() {
        jdbc.queryForObject("select set_config('edol.tenant_id', ?, true)", String.class,
                tenants.getCurrentTenantId().toString());
    }

    private void lockActive(Identity identity) {
        // Same lock order as pairing/retirement: printer, agent, authority.
        if (jdbc.queryForList("""
                select id from core.printers where id = ? and tenant_id = ? and enabled
                    and connection_mode = 'AGENT' for update
                """, UUID.class, identity.printerId(), identity.tenantId()).isEmpty()
                || jdbc.queryForList("""
                select id from core.agents where id = ? and tenant_id = ? and printer_id = ?
                    and state = 'ACTIVE' for update
                """, UUID.class, identity.agentId(), identity.tenantId(), identity.printerId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public List<Binding> catalog() {
        return jdbc.query("select * from core.agent_mqtt_catalog()", (rs, row) -> new Binding(
                rs.getObject("agent_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("printer_id", UUID.class), rs.getString("state"), rs.getBoolean("usable")));
    }

    public Binding ingress(UUID id) {
        return catalog().stream().filter(binding -> binding.agentId().equals(id) && binding.usable())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Inactive agent MQTT identity"));
    }

    public String commandTopic(UUID printer, String suffix) {
        return availableCommandTopic(printer, suffix)
                .orElseThrow(() -> new IllegalStateException("No active agent MQTT binding"));
    }

    public java.util.Optional<String> availableCommandTopic(UUID printer, String suffix) {
        if (!AgentMqttTopics.COMMANDS.contains(suffix)) throw new IllegalArgumentException("Invalid command type");
        UUID tenant = tenants.getCurrentTenantId();
        return catalog().stream().filter(binding -> binding.usable() && binding.tenantId().equals(tenant)
                        && binding.printerId().equals(printer))
                .findFirst().map(binding -> AgentMqttTopics.topic(binding.agentId(), suffix));
    }

    public void flushRevocations(UUID printer) {
        UUID tenant = tenants.getCurrentTenantId();
        for (Binding binding : catalog()) {
            if (binding.tenantId().equals(tenant) && binding.printerId().equals(printer)
                    && "REVOKE_PENDING".equals(binding.state())) {
                disable(binding);
            }
        }
    }

    @Scheduled(fixedDelayString = "${edol-core.agent.mqtt.reconcile-delay-ms:2000}")
    public void reconcile() {
        try {
            jdbc.execute("select core.expire_agent_mqtt_provisioning()");
            for (Binding binding : catalog()) {
                if ("REVOKE_PENDING".equals(binding.state())) disable(binding);
            }
        } catch (RuntimeException exception) {
            // Durable pending state is retried; never log broker responses or credentials.
            org.slf4j.LoggerFactory.getLogger(AgentMqttService.class).warn("Agent MQTT reconciliation pending");
        }
    }

    private void disable(Binding binding) {
        try {
            broker.disable(binding.agentId());
            jdbc.queryForObject("select core.confirm_agent_mqtt_revoked(?)", Object.class, binding.agentId());
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT revocation/provisioning pending");
    }

    public record Binding(UUID agentId, UUID tenantId, UUID printerId, String state, boolean usable) { }
    public record Credentials(String mqttSecret) {
        @Override public String toString() { return "AgentMqttCredentials[REDACTED]"; }
    }
}
