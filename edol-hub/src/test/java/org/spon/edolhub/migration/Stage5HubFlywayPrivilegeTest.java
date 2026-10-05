package org.spon.edolhub.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class Stage5HubFlywayPrivilegeTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcClient administratorJdbc;
    private JdbcClient flywayJdbc;
    private DataSource flywayDataSource;

    @BeforeEach
    void setUpRestrictedStage5Roles() {
        DataSource administratorDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        administratorJdbc = JdbcClient.create(administratorDataSource);
        administratorJdbc.sql("create role hub_schema_owner nologin nosuperuser nocreatedb nocreaterole noinherit nobypassrls")
                .update();
        administratorJdbc.sql("""
                        create role hub_flyway login password 'test-flyway-password'
                            nosuperuser nocreatedb nocreaterole noinherit nobypassrls
                        """).update();
        administratorJdbc.sql("""
                        create role hub_runtime login password 'test-runtime-password'
                            nosuperuser nocreatedb nocreaterole noinherit nobypassrls
                        """).update();
        administratorJdbc.sql("grant hub_schema_owner to hub_flyway").update();
        administratorJdbc.sql("""
                        do
                        $$
                        begin
                            execute format('revoke create on database %I from public', current_database());
                            execute format('grant connect on database %I to hub_flyway, hub_runtime', current_database());
                        end;
                        $$
                        """).update();
        administratorJdbc.sql("create schema hub authorization hub_schema_owner").update();
        administratorJdbc.sql("revoke all on schema hub from public").update();
        administratorJdbc.sql("grant usage on schema hub to hub_flyway, hub_runtime").update();
        flywayDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_flyway",
                "test-flyway-password"
        );
        flywayJdbc = JdbcClient.create(flywayDataSource);
    }

    @Test
    void administratorProvisionedPgcryptoAllowsRestrictedFlywayWithoutDatabaseCreate() throws IOException {
        String bootstrap = Files.readString(findProjectFile(
                "docs/deployment/stage5-disposable-postgres-bootstrap.sql"
        ));
        assertThat(bootstrap.indexOf("CREATE EXTENSION IF NOT EXISTS pgcrypto;"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(bootstrap.indexOf("CREATE ROLE core_schema_owner"));
        assertThat(administratorJdbc.sql("select count(*) from pg_extension where extname = 'pgcrypto'")
                .query(Integer.class)
                .single()).isZero();

        assertThatThrownBy(() -> flywayJdbc.sql("create extension if not exists pgcrypto").update())
                .hasStackTraceContaining("permission denied to create extension");

        administratorJdbc.sql("create extension if not exists pgcrypto").update();

        assertThat(flywayJdbc.sql("create extension if not exists pgcrypto").update()).isZero();

        Flyway flyway = Flyway.configure()
                .dataSource(flywayDataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("classpath:db/migration")
                .initSql("set role hub_schema_owner")
                .load();
        flyway.migrate();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2");
        assertThat(administratorJdbc.sql("""
                        select count(*)
                        from pg_roles
                        where rolname in ('hub_flyway', 'hub_runtime')
                          and (rolsuper or rolcreatedb or rolcreaterole or rolbypassrls)
                        """)
                .query(Integer.class)
                .single()).isZero();
        assertThat(administratorJdbc.sql("""
                        select has_database_privilege('hub_flyway', current_database(), 'create')
                        """)
                .query(Boolean.class)
                .single()).isFalse();
        assertThat(administratorJdbc.sql("""
                        select has_database_privilege('hub_runtime', current_database(), 'create')
                        """)
                .query(Boolean.class)
                .single()).isFalse();
        assertThat(administratorJdbc.sql("select pg_has_role('hub_runtime', 'hub_schema_owner', 'member')")
                .query(Boolean.class)
                .single()).isFalse();
        assertThat(administratorJdbc.sql("select has_schema_privilege('hub_runtime', 'hub', 'create')")
                .query(Boolean.class)
                .single()).isFalse();
    }

    private Path findProjectFile(String relativePath) {
        for (Path candidate : List.of(Path.of(relativePath), Path.of("..").resolve(relativePath))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath();
            }
        }

        throw new IllegalStateException("Project file is unavailable: " + relativePath);
    }
}
