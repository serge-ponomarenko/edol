package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrinterStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrinterStatsRepository extends JpaRepository<PrinterStats, Long> {

    Optional<PrinterStats> findByPrinterId(UUID printerId);
}
