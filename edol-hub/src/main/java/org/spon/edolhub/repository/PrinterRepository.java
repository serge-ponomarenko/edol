package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PrinterRepository extends JpaRepository<Printer, UUID> {

    List<Printer> findAllByTenantIdOrderByDisplayId(UUID tenantId);

    Optional<Printer> findByIdAndTenantId(UUID id, UUID tenantId);
}
