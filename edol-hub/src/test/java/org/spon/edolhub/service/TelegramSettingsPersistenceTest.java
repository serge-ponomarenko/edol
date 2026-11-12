package org.spon.edolhub.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class TelegramSettingsPersistenceTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String TOKEN = "123456:abcdefghijklmnopqrstuvwxyz_TEST_ONLY";
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final TenantContext tenants = new TenantContext();
    private JdbcTemplate admin;
    private JdbcTemplate runtime;
    private TransactionTemplate transaction;
    private TenantTelegramSettingsService service;

    @BeforeEach
    void migrateExistingV9DatabaseAndUseNonBypassRuntime() {
        var adminDataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        admin = new JdbcTemplate(adminDataSource);
        Flyway.configure().dataSource(adminDataSource).schemas("hub").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(adminDataSource).schemas("hub").locations("classpath:db/migration").target("9").load().migrate();
        admin.update("INSERT INTO hub.tenants (id, name) VALUES (?, 'First'), (?, 'Second')", first, second);
        Flyway.configure().dataSource(adminDataSource).schemas("hub").locations("classpath:db/migration").load().migrate();
        admin.execute("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'telegram_test_runtime') THEN
                        CREATE ROLE telegram_test_runtime LOGIN PASSWORD 'disposable-test-only'
                            NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
                    END IF;
                END $$
                """);
        admin.execute("GRANT USAGE ON SCHEMA hub TO telegram_test_runtime");
        admin.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON hub.telegram_settings, hub.telegram_tenant_directory TO telegram_test_runtime");
        var runtimeDataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "telegram_test_runtime", "disposable-test-only");
        runtime = new JdbcTemplate(runtimeDataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(runtimeDataSource));
        service = new TenantTelegramSettingsService(runtime, tenants,
                new TelegramTokenCipher(Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void encryptsRetainsReplacesDisablesAndClearsATokenWithoutLosingExistingTenants() {
        inTenant(first, () -> service.save(true, -100L, TOKEN, false));
        String encrypted = admin.queryForObject("SELECT encrypted_token FROM hub.telegram_settings WHERE tenant_id = ?", String.class, first);
        assertThat(encrypted).startsWith("v1.").doesNotContain(TOKEN);
        inTenant(first, () -> {
            assertThat(service.runtimeSettings().botToken()).isEqualTo(TOKEN);
            service.save(true, -101L, "", false);
            assertThat(service.runtimeSettings().chatId()).isEqualTo(-101L);
            assertThat(service.runtimeSettings().botToken()).isEqualTo(TOKEN);
            service.save(true, -101L, TOKEN + "_replacement", false);
            assertThat(service.runtimeSettings().botToken()).isEqualTo(TOKEN + "_replacement");
        });
        assertThat(service.configuredTenants()).containsExactly(first);
        inTenant(first, () -> service.save(false, -101L, "", false));
        assertThat(service.configuredTenants()).isEmpty();
        inTenant(first, () -> {
            assertThat(service.runtimeSettings().botToken()).isNull();
            assertThat(service.status().tokenConfigured()).isTrue();
            service.save(false, null, "", true);
            assertThat(service.status().tokenConfigured()).isFalse();
        });
        assertThat(admin.queryForObject("SELECT count(*) FROM hub.tenants", Integer.class)).isEqualTo(2);
    }

    @Test
    void nativeSqlAndServiceReadsCannotCrossTenantsOrRunWithoutTenantContext() {
        inTenant(first, () -> service.save(true, 100L, TOKEN, false));
        assertThat(runtime.queryForObject("SELECT count(*) FROM hub.telegram_settings", Integer.class)).isZero();
        assertThatThrownBy(service::status).isInstanceOf(MissingTenantContextException.class);
        inTenant(second, () -> {
            assertThat(service.status().tokenConfigured()).isFalse();
            assertThat(service.runtimeSettings().enabled()).isFalse();
            assertThat(runtime.update("UPDATE hub.telegram_settings SET chat_id = 2 WHERE tenant_id = ?", first)).isZero();
            assertThat(runtime.update("DELETE FROM hub.telegram_tenant_directory WHERE tenant_id = ?", first)).isZero();
        });
        assertThatThrownBy(() -> inTenant(second, () -> runtime.update(
                "INSERT INTO hub.telegram_tenant_directory (tenant_id) VALUES (?)", first)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // The explicitly global directory discloses only configured tenant IDs, never credentials/chat IDs.
        assertThat(service.configuredTenants()).containsExactly(first);
    }

    @Test
    void aBotCannotBeReusedByAnotherTenantAndConflictsRollBackTheDirectory() {
        inTenant(first, () -> service.save(true, 100L, TOKEN, false));
        assertThatThrownBy(() -> inTenant(second, () -> service.save(true, 200L, TOKEN, false)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.configuredTenants()).containsExactly(first);
        inTenant(second, () -> assertThat(service.status().tokenConfigured()).isFalse());
        assertThatThrownBy(() -> inTenant(first, () -> service.save(true, 0L, "", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void inTenant(UUID tenantId, Runnable work) {
        try (var ignored = tenants.open(tenantId)) {
            transaction.executeWithoutResult(status -> {
                runtime.queryForObject("SELECT set_config('edol.tenant_id', ?, true)", String.class, tenantId.toString());
                work.run();
            });
        }
    }
}
