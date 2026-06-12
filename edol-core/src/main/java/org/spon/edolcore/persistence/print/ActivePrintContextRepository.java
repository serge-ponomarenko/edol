package org.spon.edolcore.persistence.print;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivePrintContextRepository
        extends JpaRepository<ActivePrintContextEntity, UUID> {

    Optional<ActivePrintContextEntity> findByPrinterId(UUID printerId);

    void deleteByPrinterId(UUID printerId);
}