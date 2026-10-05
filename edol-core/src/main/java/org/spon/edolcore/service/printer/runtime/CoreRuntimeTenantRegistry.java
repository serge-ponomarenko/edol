package org.spon.edolcore.service.printer.runtime;

import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Keeps the trusted persisted tenant of each active Core runtime in memory. */
@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class CoreRuntimeTenantRegistry {

    private final CoreTenantContext tenantContext;
    private final ConcurrentMap<UUID, UUID> tenantByPrinter = new ConcurrentHashMap<>();

    CoreRuntimeTenantRegistry(CoreTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    void bindCurrentTenant(UUID printerId) {
        tenantByPrinter.put(printerId, tenantContext.getCurrentTenantId());
    }

    UUID tenantId(UUID printerId) {
        UUID tenantId = tenantByPrinter.get(printerId);
        if (tenantId == null) {
            throw new IllegalStateException("Active Core runtime has no trusted tenant binding: " + printerId);
        }
        return tenantId;
    }

    void remove(UUID printerId) {
        tenantByPrinter.remove(printerId);
    }
}
