package org.spon.edolhub.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.service.LegacyPrinterBackfillService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class HubMigrationTest {

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
        flyway(null).clean();
    }

    @Test
    void migratesCleanDatabaseWithoutInventingPrinterIdentity() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);

        assertThat(jdbc.sql("select count(*) from hub.tenants where is_default")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from hub.printers")
                .query(Integer.class).single()).isZero();

        new LegacyPrinterBackfillService(jdbc).validateAndBackfill(List.of());
    }

    @Test
    void preservesLegacyJobsUntilCorePrinterMappingIsAvailable() {
        flyway(MigrationVersion.fromVersion("3")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201', 1, 'legacy-session', 'FINISHED')
                        """).update();

        flyway(null).migrate();

        assertThat(jdbc.sql("select count(*) from hub.print_jobs where session_id = 'legacy-session'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select printer_id from hub.print_jobs where session_id = 'legacy-session'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select printer_id_uuid from hub.print_jobs where session_id = 'legacy-session'")
                .query(String.class).optional()).isEmpty();
    }

    @Test
    void backfillsTheDocumentedSingletonLegacyJobIdempotently() {
        flyway(MigrationVersion.fromVersion("3")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201', 1, 'legacy-session', 'FINISHED')
                        """).update();
        flyway(null).migrate();
        insertProjection(jdbc, printerId);

        LegacyPrinterBackfillService service = new LegacyPrinterBackfillService(jdbc);
        List<CorePrinterDto> coreCatalog = List.of(new CorePrinterDto(printerId, "P1", "Printer", true));

        service.validateAndBackfill(coreCatalog);
        service.validateAndBackfill(coreCatalog);

        assertThat(jdbc.sql("select printer_id_uuid from hub.print_jobs where session_id = 'legacy-session'")
                .query(UUID.class).single()).isEqualTo(printerId);
    }

    @Test
    void blocksMissingAndOrphanedDirectPrinterOwnership() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId);
        LegacyPrinterBackfillService service = new LegacyPrinterBackfillService(jdbc);
        List<CorePrinterDto> coreCatalog = List.of(new CorePrinterDto(printerId, "P1", "Printer", true));

        jdbc.sql("insert into hub.maintenance_definition (active) values (true)").update();

        assertThatThrownBy(() -> service.validateAndBackfill(coreCatalog))
                .hasMessageContaining("Missing or orphaned printer ownership in maintenance definitions");

        jdbc.sql("delete from hub.maintenance_definition").update();
        jdbc.sql("alter table hub.maintenance_definition drop constraint fk_maintenance_definition_printer")
                .update();
        jdbc.sql("""
                        insert into hub.maintenance_definition (active, printer_id)
                        values (true, '00000000-0000-0000-0000-000000000999')
                        """).update();

        assertThatThrownBy(() -> service.validateAndBackfill(coreCatalog))
                .hasMessageContaining("Missing or orphaned printer ownership in maintenance definitions");
    }

    @Test
    void blocksAmbiguousLegacyMappingsAndDuplicateStatistics() {
        flyway(MigrationVersion.fromVersion("3")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201', 1, 'legacy-session', 'FINISHED')
                        """).update();
        flyway(null).migrate();
        UUID firstPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        insertProjection(jdbc, firstPrinterId);
        insertProjection(jdbc, secondPrinterId);
        LegacyPrinterBackfillService service = new LegacyPrinterBackfillService(jdbc);
        List<CorePrinterDto> coreCatalog = List.of(
                new CorePrinterDto(firstPrinterId, "P1", "First", true),
                new CorePrinterDto(secondPrinterId, "P2", "Second", true)
        );

        assertThatThrownBy(() -> service.validateAndBackfill(coreCatalog))
                .hasMessageContaining("exactly one Hub/Core printer UUID mapping");

        jdbc.sql("update hub.print_jobs set printer_id_uuid = :printerId")
                .param("printerId", firstPrinterId)
                .update();
        jdbc.sql("drop index hub.uk_printer_stats_printer").update();
        jdbc.sql("insert into hub.printer_stats (printer_id) values (:printerId)")
                .param("printerId", firstPrinterId)
                .update();
        jdbc.sql("insert into hub.printer_stats (printer_id) values (:printerId)")
                .param("printerId", firstPrinterId)
                .update();

        assertThatThrownBy(() -> service.validateAndBackfill(coreCatalog))
                .hasMessageContaining("Multiple printer statistics rows map to the same printer");
    }

    @Test
    void blocksOrphanedJobsDuplicateCoreUuidAndProjectionDisagreement() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId);
        LegacyPrinterBackfillService service = new LegacyPrinterBackfillService(jdbc);
        CorePrinterDto printer = new CorePrinterDto(printerId, "P1", "Printer", true);

        assertThatThrownBy(() -> service.validateAndBackfill(List.of(printer, printer)))
                .hasMessageContaining("duplicate printer UUIDs");

        assertThatThrownBy(() -> service.validateAndBackfill(List.of()))
                .hasMessageContaining("projection does not match");

        jdbc.sql("alter table hub.print_jobs drop constraint fk_print_jobs_printer_uuid").update();
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id_uuid, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201',
                                '00000000-0000-0000-0000-000000000999',
                                'orphan-session', 'FINISHED')
                        """).update();

        assertThatThrownBy(() -> service.validateAndBackfill(List.of(printer)))
                .hasMessageContaining("Missing or orphaned printer ownership in print jobs");
    }

    private void insertProjection(JdbcClient jdbc, UUID printerId) {
        UUID tenantId = jdbc.sql("select id from hub.tenants where is_default")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into hub.printers (id, tenant_id, display_id, name, enabled, available_in_core)
                        values (:printerId, :tenantId, :displayId, 'Printer', true, true)
                        """)
                .param("printerId", printerId)
                .param("tenantId", tenantId)
                .param("displayId", "P" + printerId.toString().substring(printerId.toString().length() - 1))
                .update();
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
