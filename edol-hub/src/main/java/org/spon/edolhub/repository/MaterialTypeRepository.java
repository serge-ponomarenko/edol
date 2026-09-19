package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MaterialTypeRepository extends JpaRepository<MaterialType, Long> {

    Optional<MaterialType> findByName(String name);

    Optional<MaterialType> findByNameIgnoreCase(String part);

    List<MaterialType> findAllByTenantIdOrderByName(UUID tenantId);

    Optional<MaterialType> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<MaterialType> findByTenantIdAndNameIgnoreCase(UUID tenantId, String part);
}
