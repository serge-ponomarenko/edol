package org.spon.edolcore.persistence.printer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrinterRepository
        extends JpaRepository<Printer, UUID> {

    Optional<Printer> findByDisplayId(String displayId);

    Optional<Printer> findBySerialNumber(String serialNumber);

    Optional<Printer> findFirstByOrderByDisplayIdAsc();
}