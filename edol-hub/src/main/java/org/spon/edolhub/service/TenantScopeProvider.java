package org.spon.edolhub.service;

import java.util.Optional;

/**
 * Opens a tenant scope only from a profile-owned trusted source.
 */
public interface TenantScopeProvider {

    Optional<TenantContext.TenantScope> openIfConfigured(String entryPoint);

    TenantContext.TenantScope open(String entryPoint);
}
