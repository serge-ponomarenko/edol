package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.MaintenanceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceDefinitionRepository
        extends JpaRepository<MaintenanceDefinition, Long> {

    List<MaintenanceDefinition> findByActiveTrue();
}
