package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrintJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    List<PrintJob> findAllByOrderByStartedAtDesc();

    Page<PrintJob> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<PrintJob> findAllByPrinterIdOrderByStartedAtDesc(UUID printerId, Pageable pageable);

    Optional<PrintJob> findBySessionId(String sessionId);

    Optional<PrintJob> findByPrinterIdAndSessionId(UUID printerId, String sessionId);

    Optional<PrintJob> findByPublicId(Long publicId);

    Optional<PrintJob> findByPublicIdAndPrinterTenantId(Long publicId, UUID tenantId);
}
