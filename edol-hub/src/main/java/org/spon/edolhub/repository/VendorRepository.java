package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByName(String name);

    Optional<Vendor> findByNameIgnoreCase(String vendorName);

    List<Vendor> findAllByTenantIdOrderByName(UUID tenantId);

    Optional<Vendor> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<Vendor> findByTenantIdAndNameIgnoreCase(UUID tenantId, String vendorName);
}
