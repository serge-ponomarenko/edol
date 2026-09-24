package org.spon.edolcore.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CoreMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource dataSource;

    @BeforeEach
    void resetDatabase() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        JdbcClient.create(dataSource).sql("drop schema if exists hub cascade").update();
        flyway(null).clean();
    }

    @Test
    void migratesEmptyAndCurrentSchemasAndEnforcesPrinterOwnership() {
        flyway(null).migrate();
        JdbcClient emptySchemaJdbc = JdbcClient.create(dataSource);
        assertThat(emptySchemaJdbc.sql("select count(*) from core.printers where tenant_id is null")
                .query(Integer.class).single()).isZero();

        flyway(null).clean();
        JdbcClient.create(dataSource).sql("drop schema if exists hub cascade").update();
        flyway(MigrationVersion.fromVersion("6")).migrate();
        JdbcClient currentSchemaJdbc = JdbcClient.create(dataSource);
        insertPrinter(currentSchemaJdbc);
        currentSchemaJdbc.sql("""
                        insert into core.active_print_context (session_id, printer_id)
                        values ('00000000-0000-0000-0000-000000000201',
                                '00000000-0000-0000-0000-000000000101')
                        """).update();
        createHubProjectionTable(currentSchemaJdbc);
        insertHubProjection(currentSchemaJdbc, UUID.fromString("00000000-0000-0000-0000-000000000101"));

        flyway(null).migrate();

        assertThatThrownBy(() -> currentSchemaJdbc.sql("""
                        insert into core.active_print_context (session_id, printer_id)
                        values ('00000000-0000-0000-0000-000000000202',
                                '00000000-0000-0000-0000-000000000999')
                        """).update()).hasMessageContaining("fk_active_print_context_printer");
    }

    @Test
    void blocksCurrentSchemaWithOrphanedActivePrintContext() {
        flyway(MigrationVersion.fromVersion("6")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into core.active_print_context (session_id, printer_id)
                        values ('00000000-0000-0000-0000-000000000201',
                                '00000000-0000-0000-0000-000000000999')
                        """).update();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("orphaned printer_id exists");
    }

    @Test
    void backfillsOpaqueTenantOwnershipFromHubProjection() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        insertPrinter(jdbc);
        createHubProjectionTable(jdbc);
        insertHubProjection(jdbc, printerId, tenantId);

        flyway(null).migrate();
        flyway(null).migrate();

        assertThat(jdbc.sql("select tenant_id from core.printers where id = :printerId")
                .param("printerId", printerId)
                .query(UUID.class)
                .single()).isEqualTo(tenantId);
    }

    @Test
    void backfillsTenantOwnershipFromTheActualHubMigrationChain() {
        hubFlyway().migrate();
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbc.sql("insert into hub.tenants (id, name) values (:tenantId, 'Tenant')")
                .param("tenantId", tenantId)
                .update();
        insertActualHubProjection(jdbc, printerId, tenantId);
        insertPrinter(jdbc);

        flyway(null).migrate();

        assertThat(jdbc.sql("select tenant_id from core.printers where id = :printerId")
                .param("printerId", printerId)
                .query(UUID.class)
                .single()).isEqualTo(tenantId);
    }

    @Test
    void blocksCorePrinterMissingFromHubProjection() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        insertPrinter(jdbc);
        createHubProjectionTable(jdbc);

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Core printer is missing from Hub projection");
    }

    @Test
    void blocksCorePrinterWhenHubProjectionIsUnavailable() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        insertPrinter(jdbc);

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Hub printer projection is unavailable");
    }

    @Test
    void blocksDuplicatedHubProjectionDuringTenantBackfill() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertPrinter(jdbc);
        jdbc.sql("create schema hub").update();
        jdbc.sql("create table hub.printers (id uuid not null, tenant_id uuid not null)").update();
        insertHubProjection(jdbc, printerId, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        insertHubProjection(jdbc, printerId, UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Hub projection is duplicated");
    }

    @Test
    void blocksHubProjectionWithoutTenantDuringTenantBackfill() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertPrinter(jdbc);
        jdbc.sql("create schema hub").update();
        jdbc.sql("create table hub.printers (id uuid primary key, tenant_id uuid)").update();
        jdbc.sql("insert into hub.printers (id, tenant_id) values (:printerId, null)")
                .param("printerId", printerId)
                .update();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Hub projection tenant is missing");
    }

    private void insertPrinter(JdbcClient jdbc) {
        jdbc.sql("""
                        insert into core.printers (
                            id, display_id, name, serial_number, model, connection_mode,
                            camera_provider, enabled, created_at, updated_at
                        )
                        values (
                            '00000000-0000-0000-0000-000000000101', 'P1', 'Printer', 'SERIAL-1',
                            'BAMBU_X1C', 'DIRECT', 'RTSPS', true, current_timestamp, current_timestamp
                )
                """).update();
    }

    private void createHubProjectionTable(JdbcClient jdbc) {
        jdbc.sql("create schema hub").update();
        jdbc.sql("create table hub.printers (id uuid primary key, tenant_id uuid not null)").update();
    }

    private void insertHubProjection(JdbcClient jdbc, UUID printerId) {
        insertHubProjection(jdbc, printerId, UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    private void insertHubProjection(JdbcClient jdbc, UUID printerId, UUID tenantId) {
        jdbc.sql("insert into hub.printers (id, tenant_id) values (:printerId, :tenantId)")
                .param("printerId", printerId)
                .param("tenantId", tenantId)
                .update();
    }

    private void insertActualHubProjection(JdbcClient jdbc, UUID printerId, UUID tenantId) {
        jdbc.sql("""
                        insert into hub.printers (id, tenant_id, display_id, name, enabled, available_in_core)
                        values (:printerId, :tenantId, 'P1', 'Printer', true, true)
                        """)
                .param("printerId", printerId)
                .param("tenantId", tenantId)
                .update();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas("core")
                .defaultSchema("core")
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Flyway hubFlyway() {
        Path migrationPath = findHubMigrationPath();
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("filesystem:" + migrationPath.toString().replace('\\', '/'))
                .cleanDisabled(false)
                .load();
    }

    private Path findHubMigrationPath() {
        Path repositoryRelative = Path.of("edol-hub", "src", "main", "resources", "db", "migration");
        if (Files.isDirectory(repositoryRelative)) {
            return repositoryRelative.toAbsolutePath();
        }

        Path moduleRelative = Path.of("..", "edol-hub", "src", "main", "resources", "db", "migration");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative.toAbsolutePath();
        }

        throw new IllegalStateException("Hub Flyway migration directory is unavailable");
    }
}
