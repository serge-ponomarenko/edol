package org.spon.edolhub.migration;

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

import static org.assertj.core.api.Assertions.assertThat;

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
