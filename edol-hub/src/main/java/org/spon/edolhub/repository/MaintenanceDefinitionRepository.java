package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.MaintenanceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceDefinitionRepository
        extends JpaRepository<MaintenanceDefinition, Long> {

    List<MaintenanceDefinition> findByActiveTrue();

    List<MaintenanceDefinition> findByPrinterIdAndActiveTrue(UUID printerId);

    List<MaintenanceDefinition> findAllByPrinterId(UUID printerId);

    Optional<MaintenanceDefinition> findByIdAndPrinterId(Long id, UUID printerId);
}
