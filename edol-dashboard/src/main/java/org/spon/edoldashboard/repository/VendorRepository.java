package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByName(String name);

    Optional<Vendor> findByNameIgnoreCase(String vendorName);
}
