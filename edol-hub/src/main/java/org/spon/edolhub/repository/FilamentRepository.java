package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.Filament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilamentRepository extends JpaRepository<Filament, Long> {

    Optional<Filament> findFirstByFullIdAndColorHex(
            String fullId,
            String colorHex
    );

    Optional<Filament> findFirstByPrinterFilamentProfileIdAndColorHex(
            String printerFilamentProfileId,
            String colorHex
    );

}
