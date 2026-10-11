package org.spon.edolams.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AmsFlywayAutoConfigurationTest {

    private static final String FLYWAY_PASSWORD = "test-flyway-password";
    private static final String RUNTIME_PASSWORD = "test-runtime-password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcClient administrator;

    @BeforeEach
    void setUp() {
        administrator = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ));
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
    }

    @Test
    void appliesTerminalMigrationsThroughSpringBootFlywayAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class
                ))
                .withPropertyValues(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=ams_runtime",
                        "spring.datasource.password=" + RUNTIME_PASSWORD,
                        "spring.flyway.enabled=true",
                        "spring.flyway.schemas=ams",
                        "spring.flyway.default-schema=ams",
                        "spring.flyway.create-schemas=true",
                        "spring.flyway.user=ams_flyway",
                        "spring.flyway.password=" + FLYWAY_PASSWORD,
                        "spring.flyway.init-sqls=SET ROLE ams_schema_owner"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Flyway.class);
                    assertThat(administrator.sql("""
                                    select exists (
                                        select 1
                                        from information_schema.tables
                                        where table_schema = 'ams' and table_name = 'terminal_pairings'
                                    )
                                    """).query(Boolean.class).single()).isTrue();
                });
    }
}
