package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByName(String name);

    Optional<Vendor> findByNameIgnoreCase(String vendorName);
}
