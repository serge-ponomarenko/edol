package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.PrinterStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrinterStatsRepository extends JpaRepository<PrinterStats, Long> {
}
