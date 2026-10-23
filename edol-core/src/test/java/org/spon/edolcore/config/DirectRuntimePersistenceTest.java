package org.spon.edolcore.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.camera.CameraProvider;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.management.DefaultPrinterManagementService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.spon.edolcore.service.printer.transport.BambuMqttConnectionManager;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.tenant.MissingCoreTenantContextException;
import org.spon.edolcore.util.SslUtil;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production JPA/RLS boundaries with synthetic transport; no printer connection. */
@Testcontainers(disabledWithoutDocker = true)
class DirectRuntimePersistenceTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID PRINTER = UUID.randomUUID();
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void prepare() {
        var adminSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(adminSource);
        jdbc.execute("create role core_runtime nologin nosuperuser nobypassrls");
        Flyway.configure().dataSource(adminSource).schemas("core").defaultSchema("core")
                .locations("classpath:db/migration", "classpath:db/secure-migration").load().migrate();
        jdbc.update("""
                insert into core.printers(id, tenant_id, display_id, name, serial_number, model,
                    connection_mode, camera_provider, enabled, created_at, updated_at)
                values (?, ?, 'DIRECT-RLS', 'Synthetic', 'SYNTHETIC', 'BAMBU_P1S', 'DIRECT', 'LEGACY', true, now(), now())
                """, PRINTER, TENANT_A);
        jdbc.update("""
                insert into core.printer_connection_configurations(id, printer_id, mqtt_host, mqtt_port, access_code)
                values (?, ?, 'synthetic.invalid', 8883, 'synthetic-code')
                """, UUID.randomUUID(), PRINTER);
        jdbc.execute("create role direct_runtime_test login inherit nosuperuser nobypassrls password 'synthetic-runtime-test'");
        jdbc.execute("grant core_runtime to direct_runtime_test");
        var runtimeSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "direct_runtime_test", "synthetic-runtime-test");
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant", "bambu.client-id=synthetic",
                "bambu.connection-timeout=1", "bambu.show-raw-mqtt=false").applyTo(context);
        context.registerBean(DataSource.class, () -> runtimeSource);
        context.register(Persistence.class);
        context.registerBeanDefinition("legacyCamera", BeanDefinitionBuilder.genericBeanDefinition(
                "org.spon.edolcore.service.camera.LegacyCameraProvider").getBeanDefinition());
        context.refresh();
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @Test
    void reconnectConfigurationLoadsInTheCorrectRlsTransactionWithoutAnOuterTransaction() {
        var tenants = context.getBean(CoreTenantContext.class);
        try (var ignored = tenants.open(TENANT_A)) {
            var connection = context.getBean(BambuMqttConnectionManager.class).getConnection(PRINTER);
            assertThat(connection.getConfiguration().getMqttHost()).isEqualTo("synthetic.invalid");
        }
    }

    @Test
    void cameraConfigurationUsesTheTenantTransactionBeforeAnySyntheticNetworkRead() throws Exception {
        var factory = mock(SSLSocketFactory.class);
        var socket = mock(SSLSocket.class);
        when(factory.createSocket("synthetic.invalid", 6000)).thenReturn(socket);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        byte[] frame = new byte[]{1, 2, 3};
        byte[] response = ByteBuffer.allocate(16 + frame.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(frame.length).put(new byte[12]).put(frame).array();
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(response));
        try (var ssl = mockStatic(SslUtil.class); var ignored = context.getBean(CoreTenantContext.class).open(TENANT_A)) {
            ssl.when(SslUtil::createTrustAllSocketFactory).thenReturn(factory);
            assertThat(context.getBean(CameraProvider.class).capture(PRINTER)).containsExactly(frame);
        }
    }

    @Test
    void foreignTenantAndAbsentContextCannotReadTheCameraConfiguration() {
        CameraProvider camera = context.getBean(CameraProvider.class);
        try (var ignored = context.getBean(CoreTenantContext.class).open(TENANT_B)) {
            assertThatThrownBy(() -> camera.capture(PRINTER))
                    .isInstanceOf(org.spon.edolcore.service.printer.management.exception.PrinterNotFoundException.class);
        }
        assertThatThrownBy(() -> camera.capture(PRINTER)).isInstanceOf(MissingCoreTenantContextException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = PrinterRepository.class, bootstrapMode = BootstrapMode.LAZY)
    @Import({CorePersistenceConfiguration.class, DefaultPrinterManagementService.class, BambuMqttConnectionManager.class})
    static class Persistence {
        @Bean CoreTenantContext tenants() { return new CoreTenantContext(); }
        @Bean BambuTelemetryConsumer telemetry() { return mock(BambuTelemetryConsumer.class); }
        @Bean PrinterConnectivityStateService connectivity() { return mock(PrinterConnectivityStateService.class); }
        @Bean CoreRuntimeTenantExecutor executor() { return mock(CoreRuntimeTenantExecutor.class); }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, CoreTenantContext tenants) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("org.spon.edolcore.persistence.printer");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.default_schema", "core", "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                    "hibernate.tenant_identifier_resolver", new CoreTenantIdentifierResolver(tenants)));
            return factory;
        }
    }
}
