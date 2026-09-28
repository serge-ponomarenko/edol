package org.spon.edolhub.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.Optional;

/**
 * Temporary, observable bridge for the explicitly enumerated pre-auth ingress paths.
 */
@Service
@Slf4j
public class LegacyDefaultTenantCompatibilityScope {

    private final TenantContext tenantContext;
    private final TenantRepository tenantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Counter usageCounter;
    private final Counter unavailableCounter;
    private final String configuredTenantId;

    public LegacyDefaultTenantCompatibilityScope(
            TenantContext tenantContext,
            TenantRepository tenantRepository,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            @Value("${edol-hub.legacy-default-tenant-compatibility.tenant-id:}") String configuredTenantId
    ) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.usageCounter = Counter.builder("edol.hub.legacy_tenant_compatibility.uses")
                .description("Uses of the temporary Hub legacy tenant compatibility scope")
                .register(meterRegistry);
        this.unavailableCounter = Counter.builder("edol.hub.legacy_tenant_compatibility.unavailable")
                .description("Background compatibility entries skipped without a configured legacy tenant")
                .register(meterRegistry);
        this.configuredTenantId = configuredTenantId;
    }

    public Optional<TenantContext.TenantScope> openIfConfigured(String entryPoint) {
        if (configuredTenantId == null || configuredTenantId.isBlank()) {
            unavailableCounter.increment();
            log.warn("Legacy tenant compatibility scope skipped: entryPoint={}, reason=tenant-id-not-configured", entryPoint);
            return Optional.empty();
        }
        return Optional.of(open(entryPoint));
    }

    public TenantContext.TenantScope open(String entryPoint) {
        UUID tenantId = parseConfiguredTenantId();
        TenantContext.TenantScope scope = tenantContext.open(tenantId);
        try {
            Boolean validMigrationTenant = transactionTemplate.execute(status -> tenantRepository
                    .findById(tenantId)
                    .map(tenant -> tenant.isDefaultTenant())
                    .orElse(false));
            if (!Boolean.TRUE.equals(validMigrationTenant)) {
                throw new IllegalStateException("Configured legacy tenant is not the migration tenant");
            }
            usageCounter.increment();
            log.info("Legacy default tenant compatibility scope opened: entryPoint={}, tenantId={}", entryPoint, tenantId);
            return scope;
        } catch (RuntimeException exception) {
            scope.close();
            throw exception;
        }
    }

    private UUID parseConfiguredTenantId() {
        if (configuredTenantId == null || configuredTenantId.isBlank()) {
            throw new IllegalStateException("Legacy default tenant compatibility tenant ID is not configured");
        }
        try {
            return UUID.fromString(configuredTenantId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Legacy default tenant compatibility tenant ID is invalid", exception);
        }
    }
}
