package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrintAllocationPreviewRepository
        extends JpaRepository<PrintAllocationPreview, Long> {

    Optional<PrintAllocationPreview> findByPrintJobId(UUID printJobId);

    boolean existsByPrintJobId(UUID printJobId);

}
