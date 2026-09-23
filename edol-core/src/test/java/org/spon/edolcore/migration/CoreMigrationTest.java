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
        flyway(null).clean();
    }

    @Test
    void migratesEmptyAndCurrentSchemasAndEnforcesPrinterOwnership() {
        flyway(null).migrate();

        flyway(null).clean();
        flyway(MigrationVersion.fromVersion("6")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        insertPrinter(jdbc);
        jdbc.sql("""
                        insert into core.active_print_context (session_id, printer_id)
                        values ('00000000-0000-0000-0000-000000000201',
                                '00000000-0000-0000-0000-000000000101')
                        """).update();

        flyway(null).migrate();

        assertThatThrownBy(() -> jdbc.sql("""
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
}
