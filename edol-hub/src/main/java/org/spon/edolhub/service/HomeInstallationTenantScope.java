package org.spon.edolhub.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Trusted home-profile tenant source backed by the singleton installation row.
 */
@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
public class HomeInstallationTenantScope implements TenantScopeProvider {

    private final TenantContext tenantContext;
    private final JdbcClient jdbcClient;

    public HomeInstallationTenantScope(TenantContext tenantContext, JdbcClient jdbcClient) {
        this.tenantContext = tenantContext;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<TenantContext.TenantScope> openIfConfigured(String entryPoint) {
        return Optional.of(open(entryPoint));
    }

    @Override
    public TenantContext.TenantScope open(String entryPoint) {
        UUID tenantId = jdbcClient.sql("""
                        select tenant_id
                        from hub.home_installations
                        where singleton
                        """)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Home installation tenant is not initialized"
                ));
        return tenantContext.open(tenantId);
    }
}
