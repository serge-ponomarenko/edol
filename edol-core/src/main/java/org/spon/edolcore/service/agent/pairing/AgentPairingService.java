package org.spon.edolcore.service.agent.pairing;

import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** Tenant-managed pairing and narrowly scoped pre-tenant enrollment/authentication. */
public class AgentPairingService {
    private final JdbcTemplate jdbc;
    private final CoreTenantContext tenants;
    private final AgentCredentialCodec codec;

    public AgentPairingService(JdbcTemplate jdbc, CoreTenantContext tenants, AgentCredentialCodec codec) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.codec = codec;
    }

    @Transactional
    public Pairing create(UUID printerId, boolean rotate) {
        lockPrinter(printerId, true);
        if (!rotate && Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from core.agents where printer_id = ?
                    and (state = 'ACTIVE' or (state = 'PENDING' and expires_at > now())))
                """, Boolean.class, printerId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent already has a live pairing or enrollment");
        }
        retire(printerId);
        requireMqttRetired(printerId);
        UUID agentId = UUID.randomUUID();
        String code = codec.newCode();
        Instant expiry = jdbc.queryForObject("""
                insert into core.agents(id, tenant_id, printer_id, state, code_digest, code_key_version, expires_at)
                values (?, ?, ?, 'PENDING', ?, ?, now() + interval '5 minutes') returning expires_at
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), agentId, tenants.getCurrentTenantId(),
                printerId, codec.codeDigest(codec.version(), code), codec.version());
        return new Pairing(agentId, code, expiry);
    }

    @Transactional
    public void revoke(UUID printerId) {
        lockPrinter(printerId, false);
        retire(printerId);
    }

    @Transactional(readOnly = true)
    public void requireMqttRetired(UUID printerId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from core.agent_mqtt_authorities
                    where printer_id = ? and tenant_id = ? and state = 'REVOKE_PENDING')
                """, Boolean.class, printerId, tenants.getCurrentTenantId()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT disconnection is pending");
        }
    }

    @Transactional(readOnly = true)
    public Status status(UUID printerId) {
        // RLS plus explicit ownership: an empty result does not disclose another tenant.
        UUID tenant = tenants.getCurrentTenantId();
        var printers = jdbc.queryForList("select id from core.printers where id = ? and tenant_id = ?", UUID.class, printerId, tenant);
        if (printers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return jdbc.query("""
                select id, case when state = 'PENDING' and expires_at <= now() then 'EXPIRED' else state end,
                    created_at, expires_at, activated_at,
                    (select m.state from core.agent_mqtt_authorities m where m.agent_id = core.agents.id)
                    from core.agents
                where printer_id = ? order by created_at desc, id desc limit 1
                """, (rs, row) -> new Status(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getString(6)), printerId)
                .stream().findFirst().orElse(null);
    }

    private void lockPrinter(UUID printerId, boolean requireEnabledAgent) {
        var ids = jdbc.queryForList("""
                select id from core.printers where id = ? and tenant_id = ?
                    and (not ? or (connection_mode = 'AGENT' and enabled)) for update
                """, UUID.class, printerId, tenants.getCurrentTenantId(), requireEnabledAgent);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enabled agent printer is unavailable");
        }
    }

    private void retire(UUID printerId) {
        jdbc.update("""
                update core.agents set state = 'REVOKED', code_digest = null, code_key_version = null,
                    credential_digest = null, credential_key_version = null, revoked_at = now()
                where printer_id = ? and state in ('PENDING', 'ACTIVE')
                """, printerId);
    }

    // These single statements use fixed SECURITY DEFINER functions; no tenant supplied by the device.
    public Enrollment enroll(String code) {
        if (!codec.validCode(code)) {
            throw unauthorized();
        }
        String secret = codec.newSecret();
        Integer previous = codec.previousPairingVersion();
        var ids = jdbc.queryForList("select agent_id from core.consume_agent_pairing(?, ?, ?, ?, ?, ?)", UUID.class,
                codec.codeDigest(codec.version(), code), codec.version(),
                previous == null ? null : codec.codeDigest(previous, code), previous,
                codec.credentialDigest(codec.version(), secret), codec.version());
        if (ids.isEmpty()) {
            throw unauthorized();
        }
        return new Enrollment(ids.getFirst(), secret);
    }

    public Identity authenticate(String credential) {
        if (credential == null || !credential.matches("[0-9a-fA-F-]{36}\\.[A-Za-z0-9_-]{43}")) {
            throw unauthorized();
        }
        UUID id;
        try {
            id = UUID.fromString(credential.substring(0, 36));
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
        String secret = credential.substring(37);
        return jdbc.query("select * from core.find_agent_credential(?)", (rs, row) -> {
            if (!codec.matches(rs.getBytes("credential_digest"), rs.getInt("credential_key_version"), secret)) {
                throw unauthorized();
            }
            return new Identity(id, rs.getObject("tenant_id", UUID.class), rs.getObject("printer_id", UUID.class));
        }, id).stream().findFirst().orElseThrow(AgentPairingService::unauthorized);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent authentication failed");
    }

    public record Identity(UUID agentId, UUID tenantId, UUID printerId) { }
    public record Status(UUID agentId, String state, Instant createdAt, Instant expiresAt, Instant activatedAt,
                         String mqttState) { }
    public record Pairing(UUID agentId, String pairingCode, Instant expiresAt) {
        @Override public String toString() { return "AgentPairing[REDACTED]"; }
    }
    public record Enrollment(UUID agentId, String secret) {
        @Override public String toString() { return "AgentEnrollment[REDACTED]"; }
    }
}
