package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Filament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface FilamentRepository extends JpaRepository<Filament, Long> {

    Optional<Filament> findFirstByFullIdAndColorHexIgnoreCase(
            String fullId,
            String colorHex
    );

    Optional<Filament> findFirstByPrinterFilamentProfileIdAndColorHexIgnoreCase(
            String printerFilamentProfileId,
            String colorHex
    );

    List<Filament> findAllByTenantIdOrderByFullId(UUID tenantId);

    Optional<Filament> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<Filament> findFirstByTenantIdAndFullIdAndColorHexIgnoreCase(
            UUID tenantId,
            String fullId,
            String colorHex
    );

    Optional<Filament> findFirstByTenantIdAndPrinterFilamentProfileIdAndColorHexIgnoreCase(
            UUID tenantId,
            String printerFilamentProfileId,
            String colorHex
    );

}
