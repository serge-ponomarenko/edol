package org.spon.edolhub.migration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.PrintAllocationGroup;
import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.spon.edolhub.service.LegacyPrinterBackfillService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.io.TempDir;

@Testcontainers(disabledWithoutDocker = true)
class HubMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource dataSource;

    @TempDir
    private Path temporaryDirectory;

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
    void migratesCleanDatabaseToAnEmptyTenantDomain() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);

        assertThat(jdbc.sql("select count(*) from hub.tenants")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from hub.printers")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from hub.users")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from hub.tenant_memberships")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select nextval('hub.print_jobs_public_id_seq')")
                .query(Long.class).single()).isEqualTo(1L);

        new LegacyPrinterBackfillService(jdbc).validateOwnership(List.of());
    }

    @Test
    void retainsTheLegacyTenantWhenItOwnsData() {
        flyway(MigrationVersion.fromVersion("5")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID legacyTenantId = ensureTenant(jdbc, "Legacy tenant");
        insertProjection(jdbc, UUID.fromString("00000000-0000-0000-0000-000000000101"), legacyTenantId);

        flyway(null).migrate();

        assertThat(jdbc.sql("select count(*) from hub.tenants where id = :tenantId")
                .param("tenantId", legacyTenantId)
                .query(Integer.class).single()).isEqualTo(1);
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

    @Test
    void backfillsTenantLinksAndRejectsCrossTenantAggregateReferences() {
        flyway(MigrationVersion.fromVersion("5")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID firstTenantId = ensureTenant(jdbc, "First tenant");
        UUID secondTenantId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        jdbc.sql("insert into hub.tenants (id, name) values (:id, 'Second tenant')")
                .param("id", secondTenantId)
                .update();
        UUID firstPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, firstPrinterId, firstTenantId);
        UUID secondPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        insertProjection(jdbc, secondPrinterId, secondTenantId);
        long firstFilamentId = insertFilament(jdbc, firstTenantId, "FIRST");
        long secondFilamentId = insertFilament(jdbc, secondTenantId, "SECOND");
        long firstSpoolId = insertSpool(jdbc, firstFilamentId);
        long secondSpoolId = insertSpool(jdbc, secondFilamentId);
        UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values (:jobId, :printerId, 'session', 'FINISHED')
                        """)
                .param("jobId", jobId)
                .param("printerId", firstPrinterId)
                .update();
        long previewId = jdbc.sql("""
                        insert into hub.print_allocation_preview (finalized, print_job_id)
                        values (false, :jobId)
                        returning id
                        """)
                .param("jobId", jobId)
                .query(Long.class)
                .single();
        UUID secondJobId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values (:jobId, :printerId, 'second-session', 'FINISHED')
                        """)
                .param("jobId", secondJobId)
                .param("printerId", secondPrinterId)
                .update();
        long secondPreviewId = jdbc.sql("""
                        insert into hub.print_allocation_preview (finalized, print_job_id)
                        values (false, :jobId)
                        returning id
                        """)
                .param("jobId", secondJobId)
                .query(Long.class)
                .single();
        long groupId = jdbc.sql("""
                        insert into hub.print_allocation_group (preview_id, filament_id, status)
                        values (:previewId, :filamentId, 'RESOLVED')
                        returning id
                        """)
                .param("previewId", previewId)
                .param("filamentId", firstFilamentId)
                .query(Long.class)
                .single();
        jdbc.sql("""
                        insert into hub.print_allocation_item (group_id, filament_spool_id)
                        values (:groupId, :spoolId)
                        """)
                .param("groupId", groupId)
                .param("spoolId", firstSpoolId)
                .update();
        jdbc.sql("""
                        insert into hub.job_spool_usage (print_job_id, filament_spool_id)
                        values (:jobId, :spoolId)
                        """)
                .param("jobId", jobId)
                .param("spoolId", firstSpoolId)
                .update();

        flyway(null).migrate();

        assertThat(jdbc.sql("select tenant_id from hub.print_allocation_group where id = :id")
                .param("id", groupId)
                .query(UUID.class)
                .single()).isEqualTo(firstTenantId);
        assertThat(jdbc.sql("select tenant_id from hub.print_allocation_item where group_id = :id")
                .param("id", groupId)
                .query(UUID.class)
                .single()).isEqualTo(firstTenantId);
        assertThat(jdbc.sql("select tenant_id from hub.job_spool_usage where print_job_id = :id")
                .param("id", jobId)
                .query(UUID.class)
                .single()).isEqualTo(firstTenantId);
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.print_allocation_group (tenant_id, preview_id, filament_id, status)
                        values (:tenantId, :previewId, :filamentId, 'RESOLVED')
                        """)
                .param("tenantId", firstTenantId)
                .param("previewId", previewId)
                .param("filamentId", secondFilamentId)
                .update()).hasMessageContaining("fk_print_allocation_group_filament_tenant");
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.print_allocation_item (tenant_id, group_id, filament_spool_id)
                        values (:tenantId, :groupId, :spoolId)
                        """)
                .param("tenantId", firstTenantId)
                .param("groupId", groupId)
                .param("spoolId", secondSpoolId)
                .update()).hasMessageContaining("Print allocation item tenant must match its spool tenant");
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.job_spool_usage (tenant_id, print_job_id, filament_spool_id)
                        values (:tenantId, :jobId, :spoolId)
                        """)
                .param("tenantId", firstTenantId)
                .param("jobId", jobId)
                .param("spoolId", secondSpoolId)
                .update()).hasMessageContaining("Job spool usage tenant must match its print job and spool tenants");
        assertThatThrownBy(() -> jdbc.sql("""
                        update hub.print_allocation_group
                        set preview_id = :previewId
                        where id = :groupId
                        """)
                .param("previewId", secondPreviewId)
                .param("groupId", groupId)
                .update()).hasMessageContaining("Print allocation group tenant must match its preview printer tenant");
        assertThatThrownBy(() -> jdbc.sql("""
                        update hub.print_allocation_item
                        set filament_spool_id = :spoolId
                        where group_id = :groupId
                        """)
                .param("spoolId", secondSpoolId)
                .param("groupId", groupId)
                .update()).hasMessageContaining("Print allocation item tenant must match its spool tenant");
        assertThatThrownBy(() -> jdbc.sql("""
                        update hub.job_spool_usage
                        set filament_spool_id = :spoolId
                        where print_job_id = :jobId
                        """)
                .param("spoolId", secondSpoolId)
                .param("jobId", jobId)
                .update()).hasMessageContaining("Job spool usage tenant must match its print job and spool tenants");
    }

    @Test
    void enforcesMembershipAndOidcIdentityInvariants() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        jdbc.sql("insert into hub.tenants (id, name) values (:id, 'Tenant')")
                .param("id", tenantId)
                .update();
        jdbc.sql("""
                        insert into hub.users (id, issuer, subject)
                        values (:id, 'https://issuer.example', 'subject')
                        """)
                .param("id", userId)
                .update();
        jdbc.sql("""
                        insert into hub.tenant_memberships (id, tenant_id, user_id, role, status)
                        values ('00000000-0000-0000-0000-000000000302', :tenantId, :userId, 'OWNER', 'ACTIVE')
                        """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .update();

        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.users (id, issuer, subject)
                        values ('00000000-0000-0000-0000-000000000303', 'https://issuer.example', 'subject')
                        """).update()).hasMessageContaining("uk_users_issuer_subject");
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.tenant_memberships (id, tenant_id, user_id, role, status)
                        values ('00000000-0000-0000-0000-000000000304', :tenantId, :userId, 'MEMBER', 'ACTIVE')
                        """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .update()).hasMessageContaining("chk_tenant_memberships_role");
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into hub.tenant_memberships (id, tenant_id, user_id, role, status)
                        values ('00000000-0000-0000-0000-000000000305', :tenantId, :userId, 'OWNER', 'ACTIVE')
                        """)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .update()).hasMessageContaining("uk_tenant_memberships_tenant_user");
        assertThatThrownBy(() -> jdbc.sql("""
                        update hub.tenant_memberships
                        set status = 'REVOKED'
                        where id = '00000000-0000-0000-0000-000000000302'
                        """)
                .update()).hasMessageContaining("chk_tenant_memberships_revocation");
        assertThatThrownBy(() -> jdbc.sql("""
                        update hub.tenant_memberships
                        set status = 'PENDING'
                        where id = '00000000-0000-0000-0000-000000000302'
                        """)
                .update()).hasMessageMatching("(?s).*chk_tenant_memberships_(status|revocation).*");
        assertThatThrownBy(() -> jdbc.sql("update hub.users set subject = 'other' where id = :id")
                .param("id", userId)
                .update()).hasMessageContaining("immutable");
    }

    @Test
    void repairsTheHistoricalV2ChecksumWithoutReexecutingMigration() throws IOException {
        flyway(MigrationVersion.fromVersion("1")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into hub.print_jobs (printer_id, session_id, status)
                        values (1, 'legacy-repair-session', 'FINISHED')
                        """).update();

        Flyway historicalFlyway = flyway(
                MigrationVersion.fromVersion("2"),
                copyMigrationsWithHistoricalV2(temporaryDirectory.resolve("historical-migrations"))
        );
        historicalFlyway.migrate();
        Integer historicalChecksum = migrationChecksum(jdbc, "2");

        Flyway correctedFlyway = flyway(MigrationVersion.fromVersion("2"));
        correctedFlyway.repair();
        correctedFlyway.validate();

        assertThat(migrationChecksum(jdbc, "2")).isNotEqualTo(historicalChecksum);
        assertThat(jdbc.sql("select count(*) from hub.print_jobs")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void validatesJpaMappingsAndResolvesTenantSafeAssociations() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID tenantId = ensureTenant(jdbc, "Mapping tenant");
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertProjection(jdbc, printerId, tenantId);
        long filamentId = insertFilament(jdbc, tenantId, "MAPPING");
        long spoolId = insertSpool(jdbc, filamentId);
        UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbc.sql("""
                        insert into hub.print_jobs (id, printer_id, session_id, status)
                        values (:jobId, :printerId, 'mapping-session', 'FINISHED')
                        """)
                .param("jobId", jobId)
                .param("printerId", printerId)
                .update();
        long previewId = jdbc.sql("""
                        insert into hub.print_allocation_preview (finalized, print_job_id)
                        values (false, :jobId)
                        returning id
                        """)
                .param("jobId", jobId)
                .query(Long.class)
                .single();
        long groupId = jdbc.sql("""
                        insert into hub.print_allocation_group (tenant_id, preview_id, filament_id, status)
                        values (:tenantId, :previewId, :filamentId, 'RESOLVED')
                        returning id
                        """)
                .param("tenantId", tenantId)
                .param("previewId", previewId)
                .param("filamentId", filamentId)
                .query(Long.class)
                .single();
        long itemId = jdbc.sql("""
                        insert into hub.print_allocation_item (tenant_id, group_id, filament_spool_id)
                        values (:tenantId, :groupId, :spoolId)
                        returning id
                        """)
                .param("tenantId", tenantId)
                .param("groupId", groupId)
                .param("spoolId", spoolId)
                .query(Long.class)
                .single();

        try (EntityManagerFactory entityManagerFactory = entityManagerFactory()) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                Filament filament = entityManager.find(Filament.class, filamentId);
                PrintAllocationGroup group = entityManager.find(PrintAllocationGroup.class, groupId);
                PrintAllocationItem item = entityManager.find(PrintAllocationItem.class, itemId);

                assertThat(filament.getVendor().getName()).isEqualTo("Vendor MAPPING");
                assertThat(filament.getMaterialType().getName()).isEqualTo("Material MAPPING");
                assertThat(group.getFilament().getId()).isEqualTo(filamentId);
                assertThat(item.getGroup().getId()).isEqualTo(groupId);
            } finally {
                entityManager.close();
            }
        }
    }

    private void insertProjection(JdbcClient jdbc, UUID printerId) {
        UUID tenantId = ensureTenant(jdbc, "Tenant");
        insertProjection(jdbc, printerId, tenantId);
    }

    private void insertProjection(JdbcClient jdbc, UUID printerId, UUID tenantId) {
        jdbc.sql("""
                        insert into hub.printers (id, tenant_id, display_id, name, enabled, available_in_core)
                        values (:printerId, :tenantId, :displayId, 'Printer', true, true)
                        """)
                .param("printerId", printerId)
                .param("tenantId", tenantId)
                .param("displayId", "P" + printerId.toString().substring(printerId.toString().length() - 1))
                .update();
    }

    private UUID ensureTenant(JdbcClient jdbc, String name) {
        return jdbc.sql("select id from hub.tenants order by id limit 1")
                .query(UUID.class)
                .optional()
                .orElseGet(() -> {
                    UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                    jdbc.sql("insert into hub.tenants (id, name) values (:id, :name)")
                            .param("id", tenantId)
                            .param("name", name)
                            .update();
                    return tenantId;
                });
    }

    private long insertFilament(JdbcClient jdbc, UUID tenantId, String suffix) {
        long vendorId = jdbc.sql("""
                        insert into hub.vendors (tenant_id, name)
                        values (:tenantId, :name)
                        returning id
                        """)
                .param("tenantId", tenantId)
                .param("name", "Vendor " + suffix)
                .query(Long.class)
                .single();
        long materialTypeId = jdbc.sql("""
                        insert into hub.material_types (tenant_id, name)
                        values (:tenantId, :name)
                        returning id
                        """)
                .param("tenantId", tenantId)
                .param("name", "Material " + suffix)
                .query(Long.class)
                .single();
        return jdbc.sql("""
                        insert into hub.filaments (tenant_id, vendor_id, material_type_id)
                        values (:tenantId, :vendorId, :materialTypeId)
                        returning id
                        """)
                .param("tenantId", tenantId)
                .param("vendorId", vendorId)
                .param("materialTypeId", materialTypeId)
                .query(Long.class)
                .single();
    }

    private long insertSpool(JdbcClient jdbc, long filamentId) {
        return jdbc.sql("""
                        insert into hub.filament_spools (filament_id)
                        values (:filamentId)
                        returning id
                        """)
                .param("filamentId", filamentId)
                .query(Long.class)
                .single();
    }

    private EntityManagerFactory entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("org.spon.edolhub.model.entity");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of(
                "hibernate.default_schema", "hub",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        ));
        factory.afterPropertiesSet();
        return Objects.requireNonNull(factory.getObject());
    }

    private Flyway flyway(MigrationVersion target) {
        return flyway(target, "classpath:db/migration");
    }

    private Flyway flyway(MigrationVersion target, Path migrationLocation) {
        return flyway(target, "filesystem:" + migrationLocation.toAbsolutePath().toString().replace('\\', '/'));
    }

    private Flyway flyway(MigrationVersion target, String migrationLocation) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations(migrationLocation)
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Integer migrationChecksum(JdbcClient jdbc, String version) {
        return jdbc.sql("select checksum from hub.flyway_schema_history where version = :version")
                .param("version", version)
                .query(Integer.class)
                .single();
    }

    private Path copyMigrationsWithHistoricalV2(Path destination) throws IOException {
        Files.createDirectories(destination);
        Path source = findMigrationDirectory();
        try (Stream<Path> migrations = Files.list(source)) {
            for (Path migration : migrations.toList()) {
                Files.copy(migration, destination.resolve(migration.getFileName()));
            }
        }

        Path v2 = destination.resolve("V2__migrate_print_jobs_to_uuid.sql");
        String corrected = Files.readString(v2).replace("\r\n", "\n");
        String historical = corrected.replaceFirst(
                "(?s)COALESCE\\(\\s*\\(SELECT MAX\\(public_id\\) FROM print_jobs\\),\\s*1\\s*\\),\\s*EXISTS \\(SELECT 1 FROM print_jobs\\)",
                "COALESCE(\n                       (SELECT MAX(public_id) FROM print_jobs),\n                       0\n               )"
        );
        assertThat(historical).isNotEqualTo(corrected);
        Files.writeString(v2, historical);
        return destination;
    }

    private Path findMigrationDirectory() {
        Path moduleRelative = Path.of("src", "main", "resources", "db", "migration");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative.toAbsolutePath();
        }

        Path repositoryRelative = Path.of("edol-hub", "src", "main", "resources", "db", "migration");
        if (Files.isDirectory(repositoryRelative)) {
            return repositoryRelative.toAbsolutePath();
        }

        throw new IllegalStateException("Hub Flyway migration directory is unavailable");
    }
}
