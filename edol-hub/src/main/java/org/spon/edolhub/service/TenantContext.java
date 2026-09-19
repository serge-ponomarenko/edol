package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantContext {

    private final TenantRepository tenantRepository;

    public Tenant getCurrentTenant() {
        return tenantRepository.findByDefaultTenantTrue()
                .orElseThrow(() -> new IllegalStateException("Default tenant is not configured"));
    }

    public UUID getCurrentTenantId() {
        return getCurrentTenant().getId();
    }
}
