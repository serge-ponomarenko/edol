package org.spon.edolcore.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.tenant.MissingCoreTenantContextException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CoreTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    private final CoreTenantContext tenantContext;

    public CoreTenantIdentifierResolver(CoreTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (!tenantContext.hasCurrentTenant()) {
            throw new MissingCoreTenantContextException();
        }
        return tenantContext.getCurrentTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
