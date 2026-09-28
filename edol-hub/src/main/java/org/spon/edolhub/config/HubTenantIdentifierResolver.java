package org.spon.edolhub.config;

import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.spon.edolhub.service.TenantContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    private final TenantContext tenantContext;

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return tenantContext.getCurrentTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
