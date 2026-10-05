package org.spon.edolcore.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.EdolCoreApplication;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterCameraProvider;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.persistence.printer.PrinterModel;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.tenant.MissingCoreTenantContextException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class Stage5SmokeApplicationContextTest {

    private static final String CORE_FLYWAY = "core_flyway";
    private static final String CORE_RUNTIME = "core_runtime";
    private static final String CORE_CATALOG = "edol_core_catalog_runtime";
    private static final String HUB_FLYWAY = "hub_flyway";
    private static final String HUB_RUNTIME = "hub_runtime";
    private static final String ROLE_PASSWORD = "stage5-smoke-test-password";
    private static final String TRUSTED_SERVICE_TOKEN = "trusted-service-token";
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource administratorDataSource;

    @BeforeEach
    void prepareDisposableRoleModel() {
        administratorDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        JdbcClient jdbc = JdbcClient.create(administratorDataSource);
        jdbc.sql("drop schema if exists core cascade").update();
        jdbc.sql("drop schema if exists hub cascade").update();
        jdbc.sql("drop role if exists " + CORE_CATALOG).update();
        jdbc.sql("drop role if exists " + CORE_RUNTIME).update();
        jdbc.sql("drop role if exists " + CORE_FLYWAY).update();
        jdbc.sql("drop role if exists core_schema_owner").update();
        jdbc.sql("drop role if exists " + HUB_RUNTIME).update();
        jdbc.sql("drop role if exists " + HUB_FLYWAY).update();
        jdbc.sql("drop role if exists hub_schema_owner").update();

        jdbc.sql("create role core_schema_owner nologin nosuperuser nocreatedb nocreaterole noinherit nobypassrls").update();
        createLoginRole(jdbc, CORE_FLYWAY);
        createLoginRole(jdbc, CORE_RUNTIME);
        createLoginRole(jdbc, CORE_CATALOG);
        jdbc.sql("create role hub_schema_owner nologin nosuperuser nocreatedb nocreaterole noinherit nobypassrls").update();
        createLoginRole(jdbc, HUB_FLYWAY);
        createLoginRole(jdbc, HUB_RUNTIME);
        jdbc.sql("grant core_schema_owner to " + CORE_FLYWAY).update();
        jdbc.sql("grant hub_schema_owner to " + HUB_FLYWAY).update();
        jdbc.sql("create schema core authorization core_schema_owner").update();
        jdbc.sql("create schema hub authorization hub_schema_owner").update();
        jdbc.sql("grant usage on schema core to " + CORE_FLYWAY + ", " + CORE_RUNTIME + ", " + CORE_CATALOG).update();
        jdbc.sql("grant usage on schema hub to " + HUB_FLYWAY + ", " + HUB_RUNTIME).update();
    }

    @Test
    void startsWithoutTenantButRequiresOneForPersistence() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EdolCoreApplication.class)
                .sources(SmokeJwtDecoderConfiguration.class)
                .profiles("secure-multi-tenant", "stage5-smoke")
                .environment(applicationEnvironment())
                .run()) {
            CoreTenantContext tenantContext = context.getBean(CoreTenantContext.class);
            CoreTenantIdentifierResolver resolver = context.getBean(CoreTenantIdentifierResolver.class);
            ObjectProvider<PrinterRepository> printerRepositoryProvider = context.getBeanProvider(PrinterRepository.class);
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    context.getBean(PlatformTransactionManager.class)
            );

            assertThat(context.isActive()).isTrue();
            assertThat(tenantContext.hasCurrentTenant()).isFalse();
            assertThat(requestStatus(HttpRequest.newBuilder(endpoint(context, "/actuator/health"))
                    .GET()
                    .build())).isEqualTo(200);
            assertThat(requestStatus(HttpRequest.newBuilder(endpoint(context, "/api/printers"))
                    .GET()
                    .build())).isEqualTo(401);
            assertThat(requestStatus(HttpRequest.newBuilder(endpoint(context, "/api/printers"))
                    .header("Authorization", "Bearer " + TRUSTED_SERVICE_TOKEN)
                    .header("X-EDOL-Tenant-Id", TENANT_ID.toString())
                    .GET()
                    .build())).isEqualTo(200);
            assertThat(JdbcClient.create(administratorDataSource)
                    .sql("select has_table_privilege(:roleName, 'core.printers', 'select')")
                    .param("roleName", CORE_RUNTIME)
                    .query(Boolean.class)
                    .single()).isTrue();
            assertThat(JdbcClient.create(context.getBean(DataSource.class))
                    .sql("select current_user")
                    .query(String.class)
                    .single()).isEqualTo(CORE_RUNTIME);
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> { }))
                    .isInstanceOf(MissingCoreTenantContextException.class);

            UUID printerId = UUID.randomUUID();
            PrinterRepository printerRepository;
            try (CoreTenantContext.TenantScope ignored = tenantContext.open(TENANT_ID)) {
                assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TENANT_ID);
                printerRepository = printerRepositoryProvider.getObject();
                transactionTemplate.executeWithoutResult(status ->
                        printerRepository.saveAndFlush(printer(printerId))
                );
            }

            assertThat(JdbcClient.create(administratorDataSource)
                    .sql("select tenant_id from core.printers where id = :printerId")
                    .param("printerId", printerId)
                    .query(UUID.class)
                    .single()).isEqualTo(TENANT_ID);

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                    printerRepository.findAll()
            )).isInstanceOf(MissingCoreTenantContextException.class);
        }
    }

    private void createLoginRole(JdbcClient jdbc, String roleName) {
        jdbc.sql("create role " + roleName
                + " login nosuperuser nocreatedb nocreaterole noinherit nobypassrls password '"
                + ROLE_PASSWORD + "'")
                .update();
    }

    private Map<String, Object> applicationProperties() {
        return Map.ofEntries(
                Map.entry("edol.deployment.mode", "secure-multi-tenant"),
                Map.entry("server.port", "0"),
                Map.entry("spring.main.allow-bean-definition-overriding", "true"),
                Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()),
                Map.entry("spring.datasource.username", CORE_RUNTIME),
                Map.entry("spring.datasource.password", ROLE_PASSWORD),
                Map.entry("spring.flyway.user", CORE_FLYWAY),
                Map.entry("spring.flyway.password", ROLE_PASSWORD),
                Map.entry("edol-core.catalog-datasource.url", POSTGRES.getJdbcUrl()),
                Map.entry("edol-core.catalog-datasource.username", CORE_CATALOG),
                Map.entry("edol-core.catalog-datasource.password", ROLE_PASSWORD),
                Map.entry("spring.security.oauth2.resourceserver.jwt.issuer-uri", "https://issuer.invalid/realms/edol"),
                Map.entry("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "http://127.0.0.1:1/jwks")
        );
    }

    private StandardEnvironment applicationEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("stage5SmokeTest", applicationProperties())
        );
        return environment;
    }

    private URI endpoint(ConfigurableApplicationContext context, String path) {
        Integer port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private int requestStatus(HttpRequest request)
            throws java.io.IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SmokeJwtDecoderConfiguration {

        @Bean("jwtDecoder")
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!TRUSTED_SERVICE_TOKEN.equals(token)) {
                    throw new BadJwtException("JWT decoding is outside this persistence bootstrap test");
                }
                Instant now = Instant.now();
                return new org.springframework.security.oauth2.jwt.Jwt(
                        token,
                        now,
                        now.plusSeconds(60),
                        Map.of("alg", "none"),
                        Map.of(
                                "iss", "https://issuer.invalid/realms/edol",
                                "aud", List.of("edol-core-api"),
                                "azp", "edol-hub-service",
                                "scope", "tenant.context core.printer.read"
                        )
                );
            };
        }
    }

    private Printer printer(UUID printerId) {
        Instant now = Instant.now();
        return Printer.builder()
                .id(printerId)
                .displayId("STAGE5-SMOKE-" + printerId)
                .name("Stage 5 smoke printer")
                .serialNumber("STAGE5-SMOKE-" + printerId)
                .model(PrinterModel.UNKNOWN)
                .connectionMode(PrinterConnectionMode.AGENT)
                .cameraProvider(PrinterCameraProvider.LEGACY)
                .enabled(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
