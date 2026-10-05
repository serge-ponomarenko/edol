package org.spon.edolhub.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.config.HubTenantIdentifierResolver;
import org.spon.edolhub.config.TenantAwareJpaTransactionManager;
import org.spon.edolhub.controller.PrinterStateController;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.entity.Filament;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.PrintAllocationGroup;
import org.spon.edolhub.model.entity.PrintAllocationItem;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.model.entity.TenantMembershipStatus;
import org.spon.edolhub.model.entity.User;
import org.spon.edolhub.model.entity.Vendor;
import org.spon.edolhub.repository.FilamentSpoolRepository;
import org.spon.edolhub.repository.MaintenanceDefinitionRepository;
import org.spon.edolhub.repository.MaintenanceExecutionRepository;
import org.spon.edolhub.repository.PrinterRepository;
import org.spon.edolhub.repository.PrinterStatsRepository;
import org.spon.edolhub.repository.TenantMembershipRepository;
import org.spon.edolhub.repository.TenantRepository;
import org.spon.edolhub.repository.UserRepository;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.LegacyPrinterBackfillService;
import org.spon.edolhub.service.MaintenanceService;
import org.spon.edolhub.service.MissingTenantContextException;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.PrinterStatsService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.catchThrowable;
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
        assertThat(jdbc.sql("select count(*) from hub.legacy_tenant_bootstrap_state")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from pg_proc where oid = 'hub.claim_legacy_tenant_owner(text,text,text,text)'::regprocedure")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select nextval('hub.print_jobs_public_id_seq')")
                .query(Long.class).single()).isEqualTo(1L);

        new LegacyPrinterBackfillService(jdbc).validateOwnership(List.of());
    }

    @Test
    void claimsTheLegacyTenantExactlyOnceAfterAnExplicitBootstrapOpen() {
        flyway(MigrationVersion.fromVersion("7")).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID legacyTenantId = UUID.fromString("00000000-0000-0000-0000-000000000061");
        jdbc.sql("insert into hub.tenants (id, name, is_default) values (:id, 'Legacy tenant', true)")
                .param("id", legacyTenantId)
                .update();
        flyway(null).migrate();

        jdbc.sql("update hub.legacy_tenant_bootstrap_state set claim_open = true where singleton")
                .update();
        UUID claimedTenantId = jdbc.sql("""
                        select hub.claim_legacy_tenant_owner(
                            'https://issuer.example/realms/edol', 'owner-subject', 'Owner', null
                        )
                        """)
                .query(UUID.class)
                .single();

        assertThat(claimedTenantId).isEqualTo(legacyTenantId);
        assertThat(jdbc.sql("select count(*) from hub.users")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from hub.tenant_memberships where role = 'OWNER' and status = 'ACTIVE'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select is_default from hub.tenants where id = :id")
                .param("id", legacyTenantId)
                .query(Boolean.class).single()).isFalse();
        assertThat(jdbc.sql("select claim_open from hub.legacy_tenant_bootstrap_state where singleton")
                .query(Boolean.class).single()).isFalse();
        assertThatThrownBy(() -> jdbc.sql("""
                        select hub.claim_legacy_tenant_owner(
                            'https://issuer.example/realms/edol', 'second-subject', 'Second', null
                        )
                        """).query(UUID.class).single())
                .hasMessageContaining("bootstrap window is closed");
    }

    @Test
    void homeMigrationCreatesOneInstallationTenantAndDisablesRowLevelSecurity() {
        homeFlyway().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);

        assertThat(jdbc.sql("select count(*) from hub.tenants")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from hub.home_installations where singleton")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        select relrowsecurity
                        from pg_class
                        where oid = 'hub.tenants'::regclass
                        """)
                .query(Boolean.class).single()).isFalse();
    }

    @Test
    void homeMigrationBlocksAmbiguousTenantOwnership() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("insert into hub.tenants (id, name, is_default) values (gen_random_uuid(), 'First', true)")
                .update();
        jdbc.sql("insert into hub.tenants (id, name, is_default) values (gen_random_uuid(), 'Second', false)")
                .update();

        assertThatThrownBy(() -> homeFlyway().migrate())
                .hasMessageContaining("more than one Hub tenant exists");
    }

    @Test
    void homeInstallationTenantSupportsDiscriminatorPersistenceWithoutRlsTransactionState() {
        homeFlyway().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID tenantId = jdbc.sql("select tenant_id from hub.home_installations where singleton")
                .query(UUID.class)
                .single();
        TenantContext tenantContext = new TenantContext();

        try (EntityManagerFactory entityManagerFactory = entityManagerFactory(dataSource, tenantContext);
             TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    new JpaTransactionManager(entityManagerFactory)
            );
            transactionTemplate.executeWithoutResult(status -> {
                EntityManager entityManager = EntityManagerFactoryUtils
                        .getTransactionalEntityManager(entityManagerFactory);
                Vendor vendor = new Vendor();
                vendor.setName("Home vendor");
                entityManager.persist(vendor);
            });
        }

        assertThat(jdbc.sql("select count(*) from hub.vendors where tenant_id = :tenantId")
                .param("tenantId", tenantId)
                .query(Integer.class).single()).isEqualTo(1);
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

        TenantContext tenantContext = new TenantContext();
        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId);
             EntityManagerFactory entityManagerFactory = entityManagerFactory(tenantContext)) {
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

    @Test
    void fetchesFilamentAndSpoolTemplateGraphsInsideTheTenantBoundary() {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID tenantId = ensureTenant(jdbc, "Template tenant");
        long filamentId = insertFilament(jdbc, tenantId, "TEMPLATE");
        long spoolId = insertSpool(jdbc, filamentId);
        jdbc.sql("update hub.filament_spools set status = 'SEALED' where id = :spoolId")
                .param("spoolId", spoolId)
                .update();

        TenantContext tenantContext = new TenantContext();
        List<Filament> filaments;
        List<FilamentSpool> spools;
        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId);
             EntityManagerFactory entityManagerFactory = entityManagerFactory(tenantContext)) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                filaments = entityManager.createQuery("""
                                select filament
                                from Filament filament
                                join fetch filament.vendor
                                join fetch filament.materialType
                                where filament.tenantId = :tenantId
                                order by filament.fullId
                                """, Filament.class)
                        .setParameter("tenantId", tenantId)
                        .getResultList();
                spools = entityManager.createQuery("""
                                select spool
                                from FilamentSpool spool
                                join fetch spool.filament filament
                                join fetch filament.vendor
                                join fetch filament.materialType
                                where filament.tenantId = :tenantId
                                  and (:vendor is null or filament.vendor.name = :vendor)
                                  and (:material is null or filament.materialType.name = :material)
                                  and spool.status in :statuses
                                """, FilamentSpool.class)
                        .setParameter("tenantId", tenantId)
                        .setParameter("vendor", null)
                        .setParameter("material", null)
                        .setParameter("statuses", List.of(FilamentSpool.FilamentSpoolStatus.SEALED))
                        .getResultList();
            } finally {
                entityManager.close();
            }
        }

        assertThat(filaments).singleElement().satisfies(filament -> {
            assertThat(filament.getVendor().getName()).isEqualTo("Vendor TEMPLATE");
            assertThat(filament.getMaterialType().getName()).isEqualTo("Material TEMPLATE");
        });
        assertThat(spools).singleElement().extracting(spool -> spool.getFilament().getVendor().getName())
                .isEqualTo("Vendor TEMPLATE");
    }

    @Test
    void spoolApiRepositoryQueriesLoadThePublicSerializationGraphBeforeTheEntityManagerCloses() throws IOException {
        flyway(null).migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        UUID tenantId = ensureTenant(jdbc, "Spool API tenant");
        long filamentId = insertFilament(jdbc, tenantId, "SPOOL_API");
        long spoolId = insertSpool(jdbc, filamentId);
        jdbc.sql("update hub.filament_spools set status = 'ACTIVE' where id = :spoolId")
                .param("spoolId", spoolId)
                .update();

        FilamentSpool spoolByStatus;
        FilamentSpool spoolById;
        List<FilamentSpool> allSpools;
        TenantContext tenantContext = new TenantContext();
        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId);
             EntityManagerFactory entityManagerFactory = entityManagerFactory(tenantContext)) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                FilamentSpoolRepository repository = new JpaRepositoryFactory(entityManager)
                        .getRepository(FilamentSpoolRepository.class);
                spoolByStatus = repository.findFirstByFilamentIdAndStatus(
                        filamentId,
                        FilamentSpool.FilamentSpoolStatus.ACTIVE
                ).orElseThrow();
                spoolById = repository.findByIdAndFilamentTenantId(spoolId, tenantId).orElseThrow();
                allSpools = repository.findAllByFilamentTenantId(tenantId);
            } finally {
                entityManager.close();
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        for (FilamentSpool spool : List.of(spoolByStatus, spoolById, allSpools.getFirst())) {
            assertThat(spool.getFilament().getTenant().getName()).isEqualTo("Spool API tenant");
            assertThat(spool.getFilament().getVendor().getTenant().getName()).isEqualTo("Spool API tenant");
            assertThat(spool.getFilament().getMaterialType().getTenant().getName()).isEqualTo("Spool API tenant");
            assertThat(objectMapper.writeValueAsString(spool)).contains("Spool API tenant");
        }
    }

    @Test
    void runtimeGrantCorrectionRemovesFlywayHistoryAccessWithoutChangingFlywayAccess() throws IOException {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
        administratorJdbc.sql("""
                        DO
                        $$
                        BEGIN
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_runtime') THEN
                                CREATE ROLE hub_runtime LOGIN PASSWORD 'test-runtime-password'
                                    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_flyway') THEN
                                CREATE ROLE hub_flyway LOGIN PASSWORD 'test-flyway-password'
                                    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
                            END IF;
                        END;
                        $$
                        """).update();
        flyway(null).migrate();
        administratorJdbc.sql("grant select, insert, update, delete on hub.flyway_schema_history to hub_flyway")
                .update();

        assertThat(hasAnyTablePrivilege(administratorJdbc, "hub_runtime", "hub.flyway_schema_history")).isTrue();
        assertThat(hasAllTablePrivileges(administratorJdbc, "hub_flyway", "hub.flyway_schema_history")).isTrue();

        administratorJdbc.sql(Files.readString(findProjectFile(
                "docs/migrations/hub-stage3-runtime-grant-correction.sql"
        ))).update();

        assertThat(hasAnyTablePrivilege(administratorJdbc, "hub_runtime", "hub.flyway_schema_history")).isFalse();
        assertThat(hasAllTablePrivileges(administratorJdbc, "hub_flyway", "hub.flyway_schema_history")).isTrue();
    }

    @Test
    void runtimeRoleRlsContainsNativeSqlAndTransactionLocalTenantState() {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
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
        flyway(null).migrate();

        UUID firstTenantId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID secondTenantId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'First')")
                .param("id", firstTenantId)
                .update();
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'Second')")
                .param("id", secondTenantId)
                .update();
        long firstVendorId = administratorJdbc.sql("""
                        insert into hub.vendors (tenant_id, name)
                        values (:tenantId, 'First vendor')
                        returning id
                        """)
                .param("tenantId", firstTenantId)
                .query(Long.class)
                .single();
        administratorJdbc.sql("""
                        insert into hub.vendors (tenant_id, name)
                        values (:tenantId, 'Second vendor')
                        """)
                .param("tenantId", secondTenantId)
                .update();

        DataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_runtime",
                "test-runtime-password"
        );
        JdbcClient runtimeJdbc = JdbcClient.create(runtimeDataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(runtimeDataSource)
        );

        assertThat(runtimeJdbc.sql("select count(*) from hub.vendors")
                .query(Integer.class)
                .single()).isZero();

        Integer firstTenantVendorCount = transactionTemplate.execute(status -> {
            setTenant(runtimeJdbc, firstTenantId.toString());
            return runtimeJdbc.sql("select count(*) from hub.vendors")
                    .query(Integer.class)
                    .single();
        });
        assertThat(firstTenantVendorCount).isEqualTo(1);

        Integer derivedTenantCount = transactionTemplate.execute(status -> {
            setTenant(runtimeJdbc, firstTenantId.toString());
            return runtimeJdbc.sql("""
                            select count(*)
                            from hub.filament_spools spool
                            join hub.filaments filament on filament.id = spool.filament_id
                            where filament.vendor_id = :vendorId
                            """)
                    .param("vendorId", firstVendorId)
                    .query(Integer.class)
                    .single();
        });
        assertThat(derivedTenantCount).isZero();

        Throwable rlsViolation = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            setTenant(runtimeJdbc, firstTenantId.toString());
            runtimeJdbc.sql("""
                            insert into hub.vendors (tenant_id, name)
                            values (:tenantId, 'Denied cross-tenant write')
                            """)
                    .param("tenantId", secondTenantId)
                    .update();
        }));
        assertThat(rlsViolation.getCause()).hasMessageContaining("row-level security");

        Integer malformedContextCount = transactionTemplate.execute(status -> {
            setTenant(runtimeJdbc, "not-a-uuid");
            return runtimeJdbc.sql("select count(*) from hub.vendors")
                    .query(Integer.class)
                    .single();
        });
        assertThat(malformedContextCount).isZero();

        String pooledStateAfterCommit = transactionTemplate.execute(status -> runtimeJdbc
                .sql("select coalesce(current_setting('edol.tenant_id', true), '')")
                .query(String.class)
                .single());
        assertThat(pooledStateAfterCommit).isEmpty();
    }

    @Test
    void rollbackDoesNotLeakTenantStateThroughAReusedHikariConnection() {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
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
        flyway(null).migrate();

        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000451");
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'Rollback tenant')")
                .param("id", tenantId)
                .update();
        administratorJdbc.sql("""
                        insert into hub.vendors (tenant_id, name)
                        values (:tenantId, 'Rollback vendor')
                        """)
                .param("tenantId", tenantId)
                .update();

        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(POSTGRES.getJdbcUrl());
        configuration.setUsername("hub_runtime");
        configuration.setPassword("test-runtime-password");
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("hub-stage3-rollback-test");

        try (HikariDataSource runtimeDataSource = new HikariDataSource(configuration)) {
            JdbcClient runtimeJdbc = JdbcClient.create(runtimeDataSource);
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    new DataSourceTransactionManager(runtimeDataSource)
            );

            Integer rollbackConnectionId = transactionTemplate.execute(status -> {
                setTenant(runtimeJdbc, tenantId.toString());
                assertThat(runtimeJdbc.sql("select count(*) from hub.vendors")
                        .query(Integer.class)
                        .single()).isEqualTo(1);
                Integer connectionId = runtimeJdbc.sql("select pg_backend_pid()")
                        .query(Integer.class)
                        .single();
                status.setRollbackOnly();
                return connectionId;
            });

            Integer reusedConnectionId = transactionTemplate.execute(status -> runtimeJdbc
                    .sql("select pg_backend_pid()")
                    .query(Integer.class)
                    .single());
            String stateAfterRollback = transactionTemplate.execute(status -> runtimeJdbc
                    .sql("select coalesce(current_setting('edol.tenant_id', true), '')")
                    .query(String.class)
                    .single());
            Integer visibleWithoutTenant = transactionTemplate.execute(status -> runtimeJdbc
                    .sql("select count(*) from hub.vendors")
                    .query(Integer.class)
                    .single());

            assertThat(reusedConnectionId).isEqualTo(rollbackConnectionId);
            assertThat(stateAfterRollback).isEmpty();
            assertThat(visibleWithoutTenant).isZero();
        }
    }

    @Test
    void tenantAwareJpaTransactionsIsolateOrmAndNativeQueriesOnTheSameConnection() {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
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
        flyway(null).migrate();

        UUID firstTenantId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID secondTenantId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID firstPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000511");
        UUID secondPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000512");
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'First')")
                .param("id", firstTenantId)
                .update();
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'Second')")
                .param("id", secondTenantId)
                .update();
        insertProjection(administratorJdbc, firstPrinterId, firstTenantId);
        insertProjection(administratorJdbc, secondPrinterId, secondTenantId);

        DataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_runtime",
                "test-runtime-password"
        );
        JdbcClient runtimeJdbc = JdbcClient.create(runtimeDataSource);
        TenantContext tenantContext = new TenantContext();

        try (EntityManagerFactory runtimeEntityManagerFactory = entityManagerFactory(runtimeDataSource, tenantContext)) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    new TenantAwareJpaTransactionManager(runtimeEntityManagerFactory, runtimeDataSource, tenantContext)
            );

            try (TenantContext.TenantScope ignored = tenantContext.open(firstTenantId)) {
                List<UUID> visiblePrinterIds = transactionTemplate.execute(status -> EntityManagerFactoryUtils
                        .getTransactionalEntityManager(runtimeEntityManagerFactory)
                        .createQuery("select printer.id from Printer printer order by printer.id", UUID.class)
                        .getResultList());
                Integer nativeVisiblePrinterCount = transactionTemplate.execute(status -> runtimeJdbc
                        .sql("select count(*) from hub.printers")
                        .query(Integer.class)
                        .single());

                assertThat(visiblePrinterIds).containsExactly(firstPrinterId);
                assertThat(nativeVisiblePrinterCount).isEqualTo(1);
            }

            try (TenantContext.TenantScope ignored = tenantContext.open(secondTenantId)) {
                List<UUID> visiblePrinterIds = transactionTemplate.execute(status -> EntityManagerFactoryUtils
                        .getTransactionalEntityManager(runtimeEntityManagerFactory)
                        .createQuery("select printer.id from Printer printer order by printer.id", UUID.class)
                        .getResultList());

                assertThat(visiblePrinterIds).containsExactly(secondPrinterId);
            }
        }

        assertThat(runtimeJdbc.sql("select count(*) from hub.printers")
                .query(Integer.class)
                .single()).isZero();
    }

    @Test
    void printerStatsReadsAreReadOnlyAndTenantScopedWhileWritePathsCreateStats() {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
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
        flyway(null).migrate();

        UUID firstTenantId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID secondTenantId = UUID.fromString("00000000-0000-0000-0000-000000000602");
        UUID firstPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000611");
        UUID secondPrinterId = UUID.fromString("00000000-0000-0000-0000-000000000612");
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'First')")
                .param("id", firstTenantId)
                .update();
        administratorJdbc.sql("insert into hub.tenants (id, name) values (:id, 'Second')")
                .param("id", secondTenantId)
                .update();
        insertProjection(administratorJdbc, firstPrinterId, firstTenantId);
        insertProjection(administratorJdbc, secondPrinterId, secondTenantId);

        DataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_runtime",
                "test-runtime-password"
        );
        TenantContext tenantContext = new TenantContext();
        try (EntityManagerFactory runtimeEntityManagerFactory = entityManagerFactory(runtimeDataSource, tenantContext)) {
            TenantAwareJpaTransactionManager transactionManager = new TenantAwareJpaTransactionManager(
                    runtimeEntityManagerFactory,
                    runtimeDataSource,
                    tenantContext
            );
            TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
            TransactionTemplate readOnlyTransaction = new TransactionTemplate(transactionManager);
            readOnlyTransaction.setReadOnly(true);

            try (TenantContext.TenantScope ignored = tenantContext.open(firstTenantId)) {
                List<?> alerts = readOnlyTransaction.execute(status -> {
                    PrinterStatsService statsService = printerStatsService(runtimeEntityManagerFactory, tenantContext);
                    MaintenanceService maintenanceService = maintenanceService(runtimeEntityManagerFactory, statsService);
                    return new PrinterStateController(statsService, maintenanceService, null, null)
                            .getMaintenanceAlerts(firstPrinterId);
                });

                assertThat(alerts).isEmpty();
            }

            assertThat(administratorJdbc.sql("select count(*) from hub.printer_stats")
                    .query(Integer.class)
                    .single()).isZero();

            try (TenantContext.TenantScope ignored = tenantContext.open(firstTenantId)) {
                writeTransaction.executeWithoutResult(status -> {
                    PrinterStatsService statsService = printerStatsService(runtimeEntityManagerFactory, tenantContext);
                    Printer printer = printerRepository(runtimeEntityManagerFactory).findById(firstPrinterId).orElseThrow();
                    statsService.addPrintJob(printer, 120L, 15L);
                    PrinterStats updated = new PrinterStats();
                    updated.setTotalPrintHours(2L);
                    updated.setTotalJobs(4L);
                    updated.setTotalFilamentUsedGrams(25L);
                    statsService.updateStats(firstPrinterId, updated);
                });
            }

            assertThat(administratorJdbc.sql("""
                            select total_print_seconds
                            from hub.printer_stats
                            where printer_id = :printerId
                            """)
                    .param("printerId", firstPrinterId)
                    .query(Long.class)
                    .single()).isEqualTo(7200L);

            try (TenantContext.TenantScope ignored = tenantContext.open(secondTenantId)) {
                assertThatThrownBy(() -> writeTransaction.executeWithoutResult(status ->
                        printerStatsService(runtimeEntityManagerFactory, tenantContext).getStats(firstPrinterId)
                )).isInstanceOf(IllegalArgumentException.class);
            }
        }

        assertThat(administratorJdbc.sql("select count(*) from hub.printer_stats where printer_id = :printerId")
                .param("printerId", secondPrinterId)
                .query(Integer.class)
                .single()).isZero();
    }

    @Test
    void identityScopedPreTenantJpaQueriesLoadOnlyTheAuthenticatedUsersMemberships() {
        JdbcClient administratorJdbc = JdbcClient.create(dataSource);
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
        flyway(null).migrate();

        String issuer = "https://issuer.example/realms/edol";
        String subject = "pre-tenant-subject";
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000551");
        DataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                "hub_runtime",
                "test-runtime-password"
        );
        JdbcClient runtimeJdbc = JdbcClient.create(runtimeDataSource);
        TenantContext tenantContext = new TenantContext();
        IdentityContext identityContext = new IdentityContext();

        try (EntityManagerFactory runtimeEntityManagerFactory = entityManagerFactory(
                runtimeDataSource,
                tenantContext,
                identityContext
        )) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    new TenantAwareJpaTransactionManager(
                            runtimeEntityManagerFactory,
                            runtimeDataSource,
                            tenantContext,
                            identityContext
                    )
            );

            assertThatThrownBy(() -> transactionTemplate.execute(status -> null))
                    .isInstanceOf(MissingTenantContextException.class);

            try (IdentityContext.IdentityScope identityScope = identityContext.open(issuer, subject);
                 TenantContext.TenantScope tenantScope = tenantContext.open(tenantId)) {
                transactionTemplate.executeWithoutResult(status -> {
                    var entityManager = EntityManagerFactoryUtils
                            .getTransactionalEntityManager(runtimeEntityManagerFactory);
                    JpaRepositoryFactory repositoryFactory = new JpaRepositoryFactory(entityManager);
                    User user = new User();
                    user.setIssuer(issuer);
                    user.setSubject(subject);
                    user.setDisplayName("Pre-tenant user");
                    User persistedUser = repositoryFactory.getRepository(UserRepository.class).save(user);
                    Tenant tenant = new Tenant();
                    tenant.setId(tenantId);
                    tenant.setName("Pre-tenant");
                    tenant.setDefaultTenant(false);
                    Tenant persistedTenant = repositoryFactory.getRepository(TenantRepository.class).save(tenant);
                    TenantMembership membership = new TenantMembership();
                    membership.setTenant(persistedTenant);
                    membership.setUser(persistedUser);
                    membership.setRole(org.spon.edolhub.model.entity.TenantMembershipRole.OWNER);
                    membership.setStatus(TenantMembershipStatus.ACTIVE);
                    repositoryFactory.getRepository(TenantMembershipRepository.class).save(membership);
                });
            }

            try (IdentityContext.IdentityScope ignored = identityContext.open(issuer, subject)) {
                TenantMembership resolvedMembership = transactionTemplate.execute(status -> {
                    assertThat(runtimeJdbc.sql("select coalesce(current_setting('edol.tenant_id', true), '')")
                            .query(String.class)
                            .single()).isEmpty();
                    var entityManager = EntityManagerFactoryUtils
                            .getTransactionalEntityManager(runtimeEntityManagerFactory);
                    JpaRepositoryFactory repositoryFactory = new JpaRepositoryFactory(entityManager);
                    User user = repositoryFactory.getRepository(UserRepository.class)
                            .findByIssuerAndSubject(issuer, subject)
                            .orElseThrow();
                    List<TenantMembership> memberships = repositoryFactory.getRepository(TenantMembershipRepository.class)
                            .findAllByUserIdAndStatusOrderByCreatedAt(user.getId(), TenantMembershipStatus.ACTIVE);

                    assertThat(memberships).hasSize(1);
                    return memberships.getFirst();
                });

                assertThat(resolvedMembership.getTenant().getId()).isEqualTo(tenantId);
                assertThat(resolvedMembership.getTenant().getName()).isEqualTo("Pre-tenant");
            }
        }
    }

    private PrinterStatsService printerStatsService(
            EntityManagerFactory entityManagerFactory,
            TenantContext tenantContext
    ) {
        JpaRepositoryFactory repositoryFactory = repositoryFactory(entityManagerFactory);
        return new PrinterStatsService(
                repositoryFactory.getRepository(PrinterStatsRepository.class),
                new PrinterAccessService(repositoryFactory.getRepository(PrinterRepository.class), tenantContext)
        );
    }

    private MaintenanceService maintenanceService(
            EntityManagerFactory entityManagerFactory,
            PrinterStatsService printerStatsService
    ) {
        JpaRepositoryFactory repositoryFactory = repositoryFactory(entityManagerFactory);
        return new MaintenanceService(
                repositoryFactory.getRepository(MaintenanceDefinitionRepository.class),
                repositoryFactory.getRepository(MaintenanceExecutionRepository.class),
                printerStatsService
        );
    }

    private PrinterRepository printerRepository(EntityManagerFactory entityManagerFactory) {
        return repositoryFactory(entityManagerFactory).getRepository(PrinterRepository.class);
    }

    private JpaRepositoryFactory repositoryFactory(EntityManagerFactory entityManagerFactory) {
        return new JpaRepositoryFactory(EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory));
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

    private void setTenant(JdbcClient jdbc, String tenantId) {
        jdbc.sql("select set_config('edol.tenant_id', :tenantId, true)")
                .param("tenantId", tenantId)
                .query(String.class)
                .single();
    }

    private EntityManagerFactory entityManagerFactory(TenantContext tenantContext) {
        return entityManagerFactory(dataSource, tenantContext);
    }

    private EntityManagerFactory entityManagerFactory(DataSource entityManagerDataSource, TenantContext tenantContext) {
        return entityManagerFactory(entityManagerDataSource, tenantContext, new IdentityContext());
    }

    private EntityManagerFactory entityManagerFactory(
            DataSource entityManagerDataSource,
            TenantContext tenantContext,
            IdentityContext identityContext
    ) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(entityManagerDataSource);
        factory.setPackagesToScan("org.spon.edolhub.model.entity");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of(
                "hibernate.default_schema", "hub",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.tenant_identifier_resolver", new HubTenantIdentifierResolver(tenantContext, identityContext),
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

    private Flyway homeFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("hub")
                .defaultSchema("hub")
                .locations("classpath:db/migration", "classpath:db/home-migration")
                .initSql("select 1")
                .cleanDisabled(false)
                .load();
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

    private Path findProjectFile(String relativePath) {
        for (Path candidate : List.of(Path.of(relativePath), Path.of("..").resolve(relativePath))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath();
            }
        }

        throw new IllegalStateException("Project file is unavailable: " + relativePath);
    }

    private boolean hasAnyTablePrivilege(JdbcClient jdbc, String roleName, String tableName) {
        return jdbc.sql("""
                        select bool_or(has_table_privilege(:roleName, :tableName, requested.privilege))
                        from unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE']) as requested(privilege)
                        """)
                .param("roleName", roleName)
                .param("tableName", tableName)
                .query(Boolean.class)
                .single();
    }

    private boolean hasAllTablePrivileges(JdbcClient jdbc, String roleName, String tableName) {
        return jdbc.sql("""
                        select bool_and(has_table_privilege(:roleName, :tableName, requested.privilege))
                        from unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE']) as requested(privilege)
                        """)
                .param("roleName", roleName)
                .param("tableName", tableName)
                .query(Boolean.class)
                .single();
    }
}
