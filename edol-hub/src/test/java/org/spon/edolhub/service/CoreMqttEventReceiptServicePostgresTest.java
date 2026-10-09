package org.spon.edolhub.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edol.mqtt.CoreMqttEventEnvelope;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.repository.PrinterRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class CoreMqttEventReceiptServicePostgresTest {

    private static final String RUNTIME_PASSWORD = "test-runtime-password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource administratorDataSource;
    private JdbcClient administratorJdbc;
    private JdbcClient runtimeJdbc;
    private TransactionTemplate runtimeTransaction;

    @BeforeEach
    void resetDatabase() {
        administratorDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway.configure()
                .dataSource(administratorDataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();

        administratorJdbc = JdbcClient.create(administratorDataSource);
        createRuntimeRole();
        Flyway.configure()
                .dataSource(administratorDataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .migrate();

        DataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_runtime",
                RUNTIME_PASSWORD
        );
        runtimeJdbc = JdbcClient.create(runtimeDataSource);
        runtimeTransaction = new TransactionTemplate(new DataSourceTransactionManager(runtimeDataSource));
    }

    @Test
    void persistsOneUtcReceiptForRepeatedEventId() {
        UUID tenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-08T12:34:56.123456Z");
        createPrinterProjection(tenantId, printerId);
        CoreMqttEventReceiptService service = receiptService(tenantId, printerId);
        CoreMqttEventEnvelope envelope = envelope(eventId, tenantId, printerId, occurredAt);

        assertThat(inTenant(tenantId, () -> service.process(envelope, () -> {
        }))).isTrue();
        assertThat(inTenant(tenantId, () -> service.process(envelope, () -> {
            throw new AssertionError("Duplicate receipt must not mutate Hub state");
        }))).isFalse();

        OffsetDateTime storedOccurredAt = inTenant(tenantId, () -> runtimeJdbc.sql("""
                        select occurred_at
                        from hub.core_mqtt_event_receipts
                        where event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query(OffsetDateTime.class)
                .single());
        Integer receiptCount = inTenant(tenantId, () -> runtimeJdbc.sql("""
                        select count(*)
                        from hub.core_mqtt_event_receipts
                        where event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query(Integer.class)
                .single());

        assertThat(receiptCount).isEqualTo(1);
        assertThat(storedOccurredAt.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(storedOccurredAt.toInstant()).isEqualTo(occurredAt);
    }

    @Test
    void rollsBackReceiptWhenHubMutationFails() {
        UUID tenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        createPrinterProjection(tenantId, printerId);
        CoreMqttEventReceiptService service = receiptService(tenantId, printerId);

        assertThatThrownBy(() -> inTenant(tenantId, () -> service.process(
                envelope(eventId, tenantId, printerId, Instant.parse("2026-10-08T12:34:56Z")),
                () -> {
                    throw new IllegalStateException("Hub mutation failed");
                }
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Hub mutation failed");

        assertThat(inTenant(tenantId, () -> runtimeJdbc.sql("""
                        select count(*)
                        from hub.core_mqtt_event_receipts
                        where event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query(Integer.class)
                .single())).isZero();
    }

    @Test
    void deniesReceiptWritesOutsideTheEnvelopeTenant() {
        UUID envelopeTenantId = UUID.randomUUID();
        UUID activeTenantId = UUID.randomUUID();
        UUID printerId = UUID.randomUUID();
        createPrinterProjection(envelopeTenantId, printerId);
        CoreMqttEventReceiptService service = receiptService(envelopeTenantId, printerId);

        assertThatThrownBy(() -> inTenant(activeTenantId, () -> service.process(
                envelope(UUID.randomUUID(), envelopeTenantId, printerId, Instant.parse("2026-10-08T12:34:56Z")),
                () -> {
                }
        ))).hasRootCauseMessage("new row violates row-level security policy for table \"core_mqtt_event_receipts\"");
    }

    private void createRuntimeRole() {
        administratorJdbc.sql("""
                        DO
                        $$
                        BEGIN
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_runtime') THEN
                                CREATE ROLE hub_runtime LOGIN PASSWORD 'test-runtime-password'
                                    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
                            END IF;
                        END;
                        $$
                        """).update();
    }

    private void createPrinterProjection(UUID tenantId, UUID printerId) {
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:tenantId, :name)")
                .param("tenantId", tenantId)
                .param("name", "Tenant " + tenantId)
                .update();
        administratorJdbc.sql("""
                        insert into hub.printers (id, tenant_id, display_id, name, enabled, available_in_core)
                        values (:printerId, :tenantId, :displayId, 'Printer', true, true)
                        """)
                .param("printerId", printerId)
                .param("tenantId", tenantId)
                .param("displayId", "P" + printerId.toString().substring(0, 8))
                .update();
    }

    private CoreMqttEventReceiptService receiptService(UUID tenantId, UUID printerId) {
        PrinterRepository printerRepository = mock(PrinterRepository.class);
        Printer printer = new Printer();
        printer.setId(printerId);
        printer.setTenantId(tenantId);
        when(printerRepository.findById(printerId)).thenReturn(Optional.of(printer));
        return new CoreMqttEventReceiptService(runtimeJdbc, printerRepository);
    }

    private CoreMqttEventEnvelope envelope(UUID eventId, UUID tenantId, UUID printerId, Instant occurredAt) {
        return new CoreMqttEventEnvelope(
                2,
                eventId,
                "print.progress.changed",
                tenantId,
                printerId,
                occurredAt.toString(),
                Map.of()
        );
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return runtimeTransaction.execute(status -> {
            runtimeJdbc.sql("select set_config('edol.tenant_id', :tenantId, true)")
                    .param("tenantId", tenantId.toString())
                    .query(String.class)
                    .single();
            return work.get();
        });
    }
}
