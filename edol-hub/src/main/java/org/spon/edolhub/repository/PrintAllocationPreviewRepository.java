package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrintAllocationPreview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrintAllocationPreviewRepository
        extends JpaRepository<PrintAllocationPreview, Long> {

    Optional<PrintAllocationPreview> findByPrintJobId(Long printJobId);

}
