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

        new LegacyPrinterBackfillService(jdbc).validateOwnership(List.of());
    }

    @Test
    void blocksLegacyPrintJobsDuringContraction() {
        flyway(MigrationVersion.fromVersion("3")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201', 1, 'legacy-session', 'FINISHED')
                        """).update();
        flyway(MigrationVersion.fromVersion("4")).migrate();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Cannot contract print job printer ownership");
    }

    @Test
    void blocksOrphanedPrintJobOwnershipDuringContraction() {
        flyway(MigrationVersion.fromVersion("4")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("alter table hub.print_jobs drop constraint fk_print_jobs_printer_uuid").update();
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id_uuid, session_id, status)
                        values ('00000000-0000-0000-0000-000000000202',
                                '00000000-0000-0000-0000-000000000999', 'orphaned-session', 'FINISHED')
                        """).update();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Cannot contract print job printer ownership");
    }

    @Test
    void blocksOrphanedMaintenanceOwnershipDuringContraction() {
        flyway(MigrationVersion.fromVersion("4")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("alter table hub.maintenance_definition drop constraint fk_maintenance_definition_printer")
                .update();
        jdbc.sql("""
                        insert into hub.maintenance_definition (active, printer_id)
                        values (true, '00000000-0000-0000-0000-000000000999')
                        """).update();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Cannot contract maintenance ownership");
    }

    @Test
    void blocksDuplicatePrinterStatisticsDuringContraction() {
        flyway(MigrationVersion.fromVersion("4")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId);
        jdbc.sql("drop index hub.uk_printer_stats_printer").update();
        jdbc.sql("insert into hub.printer_stats (printer_id) values (:printerId)")
                .param("printerId", printerId)
                .update();
        jdbc.sql("insert into hub.printer_stats (printer_id) values (:printerId)")
                .param("printerId", printerId)
                .update();

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("Cannot contract printer statistics ownership");
    }

    @Test
    void contractsValidatedPrinterOwnershipAndRetainsValidation() {
        flyway(MigrationVersion.fromVersion("4")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId);
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id_uuid, session_id, status)
                        values ('00000000-0000-0000-0000-000000000201', :printerId, 'session', 'FINISHED')
                        """)
                .param("printerId", printerId)
                .update();
        jdbc.sql("insert into hub.maintenance_definition (active, printer_id) values (true, :printerId)")
                .param("printerId", printerId)
                .update();
        jdbc.sql("insert into hub.printer_stats (printer_id) values (:printerId)")
                .param("printerId", printerId)
                .update();

        flyway(null).migrate();

        assertThat(jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'hub'
                          and ((table_name = 'print_jobs' and column_name = 'printer_id')
                            or (table_name = 'maintenance_definition' and column_name = 'printer_id')
                            or (table_name = 'printer_stats' and column_name = 'printer_id'))
                          and is_nullable = 'NO'
                        """).query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'hub' and table_name = 'print_jobs' and column_name = 'printer_id_uuid'
                        """).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("""
                        select count(*)
                        from pg_constraint constraint_record
                        join pg_class relation on relation.oid = constraint_record.conrelid
                        join pg_namespace schema on schema.oid = relation.relnamespace
                        where schema.nspname = 'hub' and relation.relname = 'print_jobs'
                          and constraint_record.conname = 'fk_print_jobs_printer'
                        """).query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, status)
                        values ('00000000-0000-0000-0000-000000000202', null, 'FINISHED')
                        """).update()).hasMessageContaining("null value");
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, status)
                        values ('00000000-0000-0000-0000-000000000203',
                                '00000000-0000-0000-0000-000000000999', 'FINISHED')
                        """).update()).hasMessageContaining("fk_print_jobs_printer");

        new LegacyPrinterBackfillService(jdbc).validateOwnership(
                List.of(new CorePrinterDto(printerId, "P1", "Printer", true))
        );
    }

    @Test
    void blocksDuplicateCoreUuidAndProjectionDisagreementAfterContraction() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId);
        LegacyPrinterBackfillService service = new LegacyPrinterBackfillService(jdbc);
        CorePrinterDto printer = new CorePrinterDto(printerId, "P1", "Printer", true);

        assertThatThrownBy(() -> service.validateOwnership(List.of(printer, printer)))
                .hasMessageContaining("duplicate printer UUIDs");
        assertThatThrownBy(() -> service.validateOwnership(List.of()))
                .hasMessageContaining("projection does not match");
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
