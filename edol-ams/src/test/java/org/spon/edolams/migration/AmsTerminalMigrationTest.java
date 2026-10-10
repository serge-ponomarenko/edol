package org.spon.edolams.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class AmsTerminalMigrationTest {

    private static final String RUNTIME_PASSWORD = "test-runtime-password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcClient administrator;
    private JdbcClient runtime;

    @BeforeEach
    void setUp() {
        DataSource administratorDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        administrator = JdbcClient.create(administratorDataSource);
        administrator.sql("drop schema if exists ams cascade").update();
        administrator.sql("""
                        do $$
                        begin
                            if not exists (select 1 from pg_roles where rolname = 'ams_schema_owner') then
                                create role ams_schema_owner nologin nosuperuser nobypassrls noinherit;
                            end if;
                            if not exists (select 1 from pg_roles where rolname = 'ams_flyway') then
                                create role ams_flyway login nosuperuser nobypassrls noinherit password 'test-flyway-password';
                            end if;
                            if not exists (select 1 from pg_roles where rolname = 'ams_runtime') then
                                create role ams_runtime login nosuperuser nobypassrls noinherit password 'test-runtime-password';
                            end if;
                            if not exists (select 1 from pg_roles where rolname = 'ams_terminal_authenticator') then
                                create role ams_terminal_authenticator nologin nosuperuser bypassrls noinherit;
                            end if;
                        end;
                        $$;
                        """).update();
        administrator.sql("grant ams_schema_owner to ams_flyway").update();
        administrator.sql("grant ams_terminal_authenticator to ams_schema_owner").update();
        administrator.sql("grant create on database test to ams_schema_owner").update();
        administrator.sql("create schema ams authorization ams_schema_owner").update();
        administrator.sql("grant usage on schema ams to ams_flyway, ams_runtime").update();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "ams_flyway", "test-flyway-password")
                .schemas("ams")
                .defaultSchema("ams")
                .initSql("set role ams_schema_owner")
                .load()
                .migrate();
        runtime = JdbcClient.create(new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), "ams_runtime", RUNTIME_PASSWORD, true
        ));
    }

    @Test
    void forcesTenantRlsAndAtomicallyConsumesPairingOnlyOnce() {
        PendingPairing pairing = pendingPairing();

        assertThatThrownBy(() -> runtime.sql("select count(*) from ams.terminals").query(Long.class).single())
                .hasMessageContaining("EDOL tenant context is required");

        assertThat(consume(runtime, pairing.printerId(), pairing.codeDigest(), pairing.credentialDigest()))
                .containsExactly(pairing.terminalId());
        assertThat(consume(runtime, pairing.printerId(), pairing.codeDigest(), pairing.credentialDigest())).isEmpty();

        runtime.sql("select set_config('edol.tenant_id', :tenantId, false)")
                .param("tenantId", UUID.randomUUID().toString()).query(String.class).single();
        assertThat(runtime.sql("select count(*) from ams.terminals").query(Long.class).single()).isZero();
        assertThat(administrator.sql("select lifecycle_state from ams.terminals where id = :terminalId")
                .param("terminalId", pairing.terminalId()).query(String.class).single()).isEqualTo("ACTIVE");
    }

    @Test
    void exhaustsAPairingAfterFiveInvalidAttemptsRegardlessOfSourceRateLimit() {
        PendingPairing pairing = pendingPairing();
        byte[] invalidCodeDigest = {9, 9, 9};

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(consume(runtime, pairing.printerId(), invalidCodeDigest, pairing.credentialDigest())).isEmpty();
        }

        assertThat(consume(runtime, pairing.printerId(), pairing.codeDigest(), pairing.credentialDigest())).isEmpty();
        assertThat(administrator.sql("select failed_attempt_count from ams.terminal_pairings where id = :pairingId")
                .param("pairingId", pairing.pairingId()).query(Integer.class).single()).isEqualTo(5);
        assertThat(administrator.sql("select state from ams.terminal_pairings where id = :pairingId")
                .param("pairingId", pairing.pairingId()).query(String.class).single()).isEqualTo("EXHAUSTED");
        assertThat(administrator.sql("select lifecycle_state from ams.terminals where id = :terminalId")
                .param("terminalId", pairing.terminalId()).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    void acceptsOnlyTheExplicitPreviousPairingKeyCandidate() {
        PendingPairing pairing = pendingPairing();
        byte[] currentDigest = {8, 8, 8};

        List<UUID> consumed = runtime.sql("""
                        select terminal_id from ams.consume_terminal_pairing(
                            :printerId, :currentDigest, 2, :previousDigest, 1, :credentialDigest, 1)
                        """)
                .param("printerId", pairing.printerId())
                .param("currentDigest", currentDigest)
                .param("previousDigest", pairing.codeDigest())
                .param("credentialDigest", pairing.credentialDigest())
                .query(UUID.class).list();

        assertThat(consumed).containsExactly(pairing.terminalId());
    }

    @Test
    void rejectsNullPairingDigestCandidates() {
        PendingPairing pairing = pendingPairing();

        List<UUID> consumed = runtime.sql("""
                        select terminal_id from ams.consume_terminal_pairing(
                            :printerId, null::bytea, 1, null::bytea, null::integer, :credentialDigest, 1)
                        """)
                .param("printerId", pairing.printerId())
                .param("credentialDigest", pairing.credentialDigest())
                .query(UUID.class).list();

        assertThat(consumed).isEmpty();
        assertThat(administrator.sql("select failed_attempt_count from ams.terminal_pairings where id = :pairingId")
                .param("pairingId", pairing.pairingId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void permitsOnlyOneConcurrentPairingConsumption() throws Exception {
        PendingPairing pairing = pendingPairing();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentConsume(pairing, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentConsume(pairing, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(1, 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void grantsTheNarrowFunctionToRuntimeButNotPublic() {
        String signature = "ams.consume_terminal_pairing(uuid,bytea,integer,bytea,integer,bytea,integer)";

        assertThat(administrator.sql("select has_function_privilege('public', :signature, 'EXECUTE')")
                .param("signature", signature).query(Boolean.class).single()).isFalse();
        assertThat(runtime.sql("select has_function_privilege(current_user, :signature, 'EXECUTE')")
                .param("signature", signature).query(Boolean.class).single()).isTrue();
        assertThat(administrator.sql("""
                        select pg_get_userbyid(proowner)
                        from pg_proc
                        where oid = :signature::regprocedure
                        """)
                .param("signature", signature).query(String.class).single()).isEqualTo("ams_terminal_authenticator");
        assertThat(administrator.sql("select rolbypassrls from pg_roles where rolname = 'ams_runtime'")
                .query(Boolean.class).single()).isFalse();
        assertThat(administrator.sql("select rolcanlogin from pg_roles where rolname = 'ams_terminal_authenticator'")
                .query(Boolean.class).single()).isFalse();
        assertThat(administrator.sql("select rolinherit from pg_roles where rolname = 'ams_terminal_authenticator'")
                .query(Boolean.class).single()).isFalse();
        assertThat(administrator.sql("select rolbypassrls from pg_roles where rolname = 'ams_terminal_authenticator'")
                .query(Boolean.class).single()).isTrue();
    }

    private PendingPairing pendingPairing() {
        UUID tenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        UUID pairingId = UUID.randomUUID();
        byte[] codeDigest = {1, 2, 3};
        byte[] credentialDigest = {4, 5, 6};
        administrator.sql("""
                        insert into ams.terminals (id, tenant_id, allowed_printer_id, lifecycle_state, created_at)
                        values (:terminalId, :tenantId, :printerId, 'PENDING', :createdAt)
                        """)
                .param("terminalId", terminalId).param("tenantId", tenantId).param("printerId", printerId)
                .param("createdAt", Timestamp.from(Instant.now())).update();
        administrator.sql("""
                        insert into ams.terminal_pairings (id, terminal_id, tenant_id, allowed_printer_id, code_digest,
                                                           code_key_version, state, expires_at, created_at)
                        values (:pairingId, :terminalId, :tenantId, :printerId, :codeDigest, 1, 'PENDING',
                                now() + interval '5 minutes', now())
                        """)
                .param("pairingId", pairingId).param("terminalId", terminalId).param("tenantId", tenantId)
                .param("printerId", printerId).param("codeDigest", codeDigest).update();
        return new PendingPairing(pairingId, terminalId, printerId, codeDigest, credentialDigest);
    }

    private List<UUID> consume(JdbcClient client, UUID printerId, byte[] codeDigest, byte[] credentialDigest) {
        return client.sql("""
                        select terminal_id from ams.consume_terminal_pairing(
                            :printerId, :codeDigest, 1, null::bytea, null::integer, :credentialDigest, 1)
                        """)
                .param("printerId", printerId).param("codeDigest", codeDigest)
                .param("credentialDigest", credentialDigest).query(UUID.class).list();
    }

    private int concurrentConsume(PendingPairing pairing, CountDownLatch ready, CountDownLatch start) throws Exception {
        JdbcClient isolatedRuntime = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "ams_runtime", RUNTIME_PASSWORD
        ));
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent pairing consumers were not released");
        }
        return consume(isolatedRuntime, pairing.printerId(), pairing.codeDigest(), pairing.credentialDigest()).size();
    }

    private record PendingPairing(
            UUID pairingId,
            UUID terminalId,
            UUID printerId,
            byte[] codeDigest,
            byte[] credentialDigest
    ) {
    }
}
