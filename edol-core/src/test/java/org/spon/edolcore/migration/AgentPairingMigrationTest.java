package org.spon.edolcore.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.agent.pairing.AgentPairingProperties;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AgentPairingMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private JdbcTemplate admin;
    private JdbcTemplate runtime;
    private TransactionTemplate transactions;
    private AgentPairingService service;
    private CoreTenantContext tenants;
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID printerA = UUID.randomUUID();
    private final UUID printerB = UUID.randomUUID();

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        admin = new JdbcTemplate(source);
        admin.execute("drop schema if exists core cascade");
        admin.execute("""
                do $$ begin if not exists(select 1 from pg_roles where rolname = 'core_runtime') then
                    create role core_runtime nologin nosuperuser nobypassrls; end if; end $$
                """);
        Flyway.configure().dataSource(source).schemas("core").defaultSchema("core")
                .locations("classpath:db/migration", "classpath:db/secure-migration").load().migrate();
        insertPrinter(printerA, tenantA);
        insertPrinter(printerB, tenantB);
        admin.execute("""
                do $$ begin if not exists(select 1 from pg_roles where rolname = 'core_agent_test') then
                    create role core_agent_test login nosuperuser noinherit nobypassrls
                        password 'synthetic-agent-test'; end if; end $$
                """);
        admin.execute("grant core_runtime to core_agent_test");
        var runtimeSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "core_agent_test", "synthetic-agent-test");
        var restricted = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = runtimeSource.getConnection();
                try (var statement = connection.createStatement()) {
                    statement.execute("set role core_runtime");
                }
                return connection;
            }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                return getConnection();
            }
        };
        runtime = new JdbcTemplate(restricted);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(restricted));
        tenants = new CoreTenantContext();
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        service = new AgentPairingService(runtime, tenants,
                new AgentCredentialCodec(new AgentPairingProperties(1, Map.of(1, key), Map.of(1, key))));
    }

    private void insertPrinter(UUID id, UUID tenant) {
        admin.update("""
                insert into core.printers(id, display_id, name, serial_number, model, connection_mode,
                    camera_provider, enabled, tenant_id, created_at, updated_at)
                values (?, ?, 'Synthetic', ?, 'BAMBU_P1S', 'AGENT', 'AGENT', true, ?, now(), now())
                """, id, id.toString(), id.toString(), tenant);
    }

    private <T> T asTenant(UUID tenant, Callable<T> action) {
        try (var ignored = tenants.open(tenant)) {
            return transactions.execute(status -> {
                runtime.queryForObject("select set_config('edol.tenant_id', ?, true)", String.class, tenant.toString());
                try {
                    return action.call();
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @Test
    void pairsOnceAndAuthenticatesOnlyThePersistedTenantPrinterUntilRevocation() {
        var pairing = asTenant(tenantA, () -> service.create(printerA, false));
        assertThat(admin.queryForObject("select octet_length(code_digest) from core.agents where id = ?", Integer.class,
                pairing.agentId())).isEqualTo(32);
        var enrolled = service.enroll(pairing.pairingCode());
        String credential = enrolled.agentId() + "." + enrolled.secret();
        var identity = service.authenticate(credential);
        assertThat(identity.tenantId()).isEqualTo(tenantA);
        assertThat(identity.printerId()).isEqualTo(printerA);
        assertThatThrownBy(() -> service.enroll(pairing.pairingCode())).hasMessageContaining("401");
        assertThatThrownBy(() -> service.authenticate(enrolled.agentId() + "." + "x".repeat(43)))
                .hasMessageContaining("401");
        asTenant(tenantA, () -> { service.revoke(printerA); return null; });
        assertThatThrownBy(() -> service.authenticate(credential)).hasMessageContaining("401");
        assertThat(admin.queryForObject("select credential_digest is null from core.agents where id = ?",
                Boolean.class, enrolled.agentId())).isTrue();
    }

    @Test
    void enforcesRlsForeignPrinterDenialAndNarrowFunctionPrivileges() {
        asTenant(tenantA, () -> service.create(printerA, false));
        asTenant(tenantB, () -> service.create(printerB, false));
        assertThat(runtime.queryForObject("select count(*) from core.agents", Integer.class)).isZero();
        assertThat(asTenant(tenantA, () -> runtime.queryForObject("select count(*) from core.agents", Integer.class))).isEqualTo(1);
        assertThatThrownBy(() -> asTenant(tenantB, () -> service.create(printerA, true))).hasMessageContaining("404");
        assertThatThrownBy(() -> asTenant(tenantB, () -> service.status(printerA))).hasMessageContaining("404");
        assertThatThrownBy(() -> runtime.execute("set role core_agent_authenticator")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(admin.queryForObject("""
                select not rolcanlogin and not rolsuper and not rolbypassrls and not rolinherit
                from pg_roles where rolname = 'core_agent_authenticator'
                """, Boolean.class)).isTrue();
        assertThat(admin.queryForObject("""
                select exists(select 1 from pg_proc p,
                    lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                    where p.oid = 'core.find_agent_credential(uuid)'::regprocedure
                    and a.grantee = 0 and a.privilege_type = 'EXECUTE')
                """, Boolean.class)).isFalse();
        assertThat(asTenant(tenantB, () -> runtime.update("""
                update core.agents set tenant_id = ? where printer_id = ?
                """, tenantA, printerA))).isZero();
    }

    @Test
    void expiredReissuedRotatedAndDisabledPrintersCannotRecoverOldAccess() {
        var old = asTenant(tenantA, () -> service.create(printerA, false));
        assertThatThrownBy(() -> asTenant(tenantA, () -> service.create(printerA, false))).hasMessageContaining("409");
        admin.update("update core.agents set expires_at = now() - interval '1 second' where id = ?", old.agentId());
        assertThatThrownBy(() -> service.enroll(old.pairingCode())).hasMessageContaining("401");
        assertThat(asTenant(tenantA, () -> service.status(printerA)).state()).isEqualTo("EXPIRED");
        var fresh = asTenant(tenantA, () -> service.create(printerA, false));
        var enrolled = service.enroll(fresh.pairingCode());
        String credential = enrolled.agentId() + "." + enrolled.secret();
        var rotated = asTenant(tenantA, () -> service.create(printerA, true));
        assertThatThrownBy(() -> service.authenticate(credential)).hasMessageContaining("401");
        admin.update("update core.printers set enabled = false where id = ?", printerA);
        assertThatThrownBy(() -> service.enroll(rotated.pairingCode())).hasMessageContaining("401");
        asTenant(tenantA, () -> { service.revoke(printerA); return null; });
        assertThat(asTenant(tenantA, () -> service.status(printerA)).state()).isEqualTo("REVOKED");
    }

    @Test
    void concurrentEnrollmentHasExactlyOneWinner() throws Exception {
        var pairing = asTenant(tenantA, () -> service.create(printerA, false));
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                gate.await();
                try {
                    service.enroll(pairing.pairingCode());
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    assertThat(exception.getStatusCode().value()).isEqualTo(401);
                    return false;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            gate.countDown();
            assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }
}
