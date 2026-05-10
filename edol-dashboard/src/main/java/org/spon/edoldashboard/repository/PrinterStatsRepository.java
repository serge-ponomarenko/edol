package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.PrinterStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrinterStatsRepository extends JpaRepository<PrinterStats, Long> {
}
