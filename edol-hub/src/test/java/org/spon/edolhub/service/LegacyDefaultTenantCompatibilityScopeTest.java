package org.spon.edolhub.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.repository.TenantRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyDefaultTenantCompatibilityScopeTest {

    private static final UUID MIGRATION_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void opensOnlyForTheConfiguredDefaultMigrationTenantAndCleansUp() {
        TenantContext tenantContext = new TenantContext();
        TenantRepository tenantRepository = mock(TenantRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        Tenant tenant = new Tenant();
        tenant.setDefaultTenant(true);
        when(tenantRepository.findById(MIGRATION_TENANT_ID)).thenReturn(Optional.of(tenant));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LegacyDefaultTenantCompatibilityScope scope = new LegacyDefaultTenantCompatibilityScope(
                tenantContext,
                tenantRepository,
                transactionManager,
                meterRegistry,
                MIGRATION_TENANT_ID.toString()
        );

        try (TenantContext.TenantScope ignored = scope.open("test-http-ingress")) {
            assertThat(tenantContext.getCurrentTenantId()).isEqualTo(MIGRATION_TENANT_ID);
        }

        assertThatThrownBy(tenantContext::getCurrentTenantId)
                .isInstanceOf(MissingTenantContextException.class);
        assertThat(meterRegistry.get("edol.hub.legacy_tenant_compatibility.uses").counter().count())
                .isEqualTo(1.0);
        verify(tenantRepository).findById(MIGRATION_TENANT_ID);
        verify(transactionManager).commit(any());
    }

    @Test
    void skipsOptionalBackgroundCompatibilityWithoutConfiguration() {
        TenantContext tenantContext = new TenantContext();
        TenantRepository tenantRepository = mock(TenantRepository.class);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LegacyDefaultTenantCompatibilityScope scope = new LegacyDefaultTenantCompatibilityScope(
                tenantContext,
                tenantRepository,
                transactionManager(),
                meterRegistry,
                ""
        );

        assertThat(scope.openIfConfigured("scheduled-ingress")).isEmpty();
        assertThatThrownBy(tenantContext::getCurrentTenantId)
                .isInstanceOf(MissingTenantContextException.class);
        assertThat(meterRegistry.get("edol.hub.legacy_tenant_compatibility.unavailable").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rejectsANonDefaultConfiguredTenantAndCleansUpContext() {
        TenantContext tenantContext = new TenantContext();
        TenantRepository tenantRepository = mock(TenantRepository.class);
        Tenant tenant = new Tenant();
        tenant.setDefaultTenant(false);
        when(tenantRepository.findById(MIGRATION_TENANT_ID)).thenReturn(Optional.of(tenant));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LegacyDefaultTenantCompatibilityScope scope = new LegacyDefaultTenantCompatibilityScope(
                tenantContext,
                tenantRepository,
                transactionManager(),
                meterRegistry,
                MIGRATION_TENANT_ID.toString()
        );

        assertThatThrownBy(() -> scope.open("test-http-ingress"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured legacy tenant is not the migration tenant");
        assertThatThrownBy(tenantContext::getCurrentTenantId)
                .isInstanceOf(MissingTenantContextException.class);
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }
}
