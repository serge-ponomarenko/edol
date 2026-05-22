package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.MaintenanceExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaintenanceExecutionRepository
        extends JpaRepository<MaintenanceExecution, Long> {

    Optional<MaintenanceExecution> findTopByMaintenanceIdOrderByExecutedAtDesc(Long id);
}
