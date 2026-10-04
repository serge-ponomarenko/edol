package org.spon.edolhub.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.MissingTenantContextException;
import org.spon.edolhub.service.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HubTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    static final UUID PRE_TENANT_IDENTIFIER = new UUID(0L, 0L);

    private final TenantContext tenantContext;
    private final IdentityContext identityContext;

    @Autowired
    public HubTenantIdentifierResolver(TenantContext tenantContext, IdentityContext identityContext) {
        this.tenantContext = tenantContext;
        this.identityContext = identityContext;
    }

    public HubTenantIdentifierResolver(TenantContext tenantContext) {
        this(tenantContext, new IdentityContext());
    }

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (tenantContext.hasCurrentTenant()) {
            return tenantContext.getCurrentTenantId();
        }
        if (identityContext.hasCurrentIdentity()) {
            return PRE_TENANT_IDENTIFIER;
        }
        throw new MissingTenantContextException();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
