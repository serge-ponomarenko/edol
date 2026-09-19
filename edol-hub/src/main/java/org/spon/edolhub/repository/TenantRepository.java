package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByDefaultTenantTrue();
}
