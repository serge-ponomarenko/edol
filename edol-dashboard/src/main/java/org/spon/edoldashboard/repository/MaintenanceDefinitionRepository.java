package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.MaintenanceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceDefinitionRepository
        extends JpaRepository<MaintenanceDefinition, Long> {

    List<MaintenanceDefinition> findByActiveTrue();
}
