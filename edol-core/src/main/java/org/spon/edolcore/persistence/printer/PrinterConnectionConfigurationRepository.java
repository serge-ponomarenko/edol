package org.spon.edolcore.persistence.printer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrinterConnectionConfigurationRepository
        extends JpaRepository<PrinterConnectionConfiguration, UUID> {

    Optional<PrinterConnectionConfiguration> findByPrinterId(UUID printerId);

    Optional<PrinterConnectionConfiguration> findByAgentId(String agentId);

    void deleteByPrinterId(UUID printerId);
}