package org.spon.edolcore.persistence.printer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrinterProvisioningRequestRepository extends JpaRepository<PrinterProvisioningRequest, UUID> {
}
