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
import java.util.UUID;

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
        administrator.sql("drop role if exists ams_runtime").update();
        administrator.sql("create role ams_runtime login password 'test-runtime-password'").update();
        Flyway.configure()
                .dataSource(administratorDataSource)
                .schemas("ams")
                .defaultSchema("ams")
                .createSchemas(true)
                .load()
                .migrate();
        runtime = JdbcClient.create(new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), "ams_runtime", RUNTIME_PASSWORD, true
        ));
    }

    @Test
    void forcesTenantRlsAndAtomicallyConsumesPairingOnlyOnce() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID printerA = UUID.randomUUID();
        UUID terminalA = UUID.randomUUID();
        UUID pairingA = UUID.randomUUID();
        byte[] codeDigest = {1, 2, 3};
        byte[] credentialDigest = {4, 5, 6};

        administrator.sql("""
                        insert into ams.terminals (id, tenant_id, allowed_printer_id, lifecycle_state, created_at)
                        values (:terminalId, :tenantId, :printerId, 'PENDING', :createdAt)
                        """)
                .param("terminalId", terminalA).param("tenantId", tenantA).param("printerId", printerA)
                .param("createdAt", Timestamp.from(Instant.now())).update();
        administrator.sql("""
                        insert into ams.terminal_pairings (id, terminal_id, tenant_id, allowed_printer_id, code_digest,
                                                           code_key_version, state, expires_at, created_at)
                        values (:pairingId, :terminalId, :tenantId, :printerId, :codeDigest, 1, 'PENDING',
                                now() + interval '5 minutes', now())
                        """)
                .param("pairingId", pairingA).param("terminalId", terminalA).param("tenantId", tenantA)
                .param("printerId", printerA).param("codeDigest", codeDigest).update();

        assertThatThrownBy(() -> runtime.sql("select count(*) from ams.terminals").query(Long.class).single())
                .hasMessageContaining("EDOL tenant context is required");

        runtime.sql("select set_config('edol.tenant_id', :tenantId, false)")
                .param("tenantId", tenantB.toString()).query(String.class).single();
        assertThat(runtime.sql("select count(*) from ams.terminals").query(Long.class).single()).isZero();

        UUID consumed = runtime.sql("""
                        select terminal_id from ams.consume_terminal_pairing(
                            :printerId, :codeDigest, :credentialDigest, 1)
                        """)
                .param("printerId", printerA).param("codeDigest", codeDigest)
                .param("credentialDigest", credentialDigest).query(UUID.class).single();
        assertThat(consumed).isEqualTo(terminalA);
        assertThat(runtime.sql("""
                        select count(*) from ams.consume_terminal_pairing(
                            :printerId, :codeDigest, :credentialDigest, 1)
                        """)
                .param("printerId", printerA).param("codeDigest", codeDigest)
                .param("credentialDigest", credentialDigest).query(Long.class).single()).isZero();

        administrator.sql("select set_config('edol.tenant_id', :tenantId, false)")
                .param("tenantId", tenantA.toString()).query(String.class).single();
        assertThat(administrator.sql("select lifecycle_state from ams.terminals where id = :terminalId")
                .param("terminalId", terminalA).query(String.class).single()).isEqualTo("ACTIVE");
    }
}
