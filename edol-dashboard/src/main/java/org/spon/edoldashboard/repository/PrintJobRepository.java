package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.PrintJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    List<PrintJob> findAllByOrderByStartedAtDesc();

    Page<PrintJob> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<PrintJob> findBySessionId(String sessionId);
}
