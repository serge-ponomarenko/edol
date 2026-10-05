package org.spon.edolcore.service.printer.runtime;

import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureCoreRuntimeTenantExecutor implements CoreRuntimeTenantExecutor {

    private final CoreTenantContext tenantContext;
    private final CoreRuntimeTenantRegistry runtimeTenantRegistry;

    SecureCoreRuntimeTenantExecutor(
            CoreTenantContext tenantContext,
            CoreRuntimeTenantRegistry runtimeTenantRegistry
    ) {
        this.tenantContext = tenantContext;
        this.runtimeTenantRegistry = runtimeTenantRegistry;
    }

    @Override
    public void execute(CoreRuntimeCatalogEntry entry, Runnable work) {
        try (CoreTenantContext.TenantScope ignored = tenantContext.open(entry.tenantId())) {
            work.run();
        }
    }

    @Override
    public void execute(java.util.UUID printerId, Runnable work) {
        try (CoreTenantContext.TenantScope ignored = tenantContext.open(runtimeTenantRegistry.tenantId(printerId))) {
            work.run();
        }
    }
}
