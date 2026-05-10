package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.Filament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilamentRepository extends JpaRepository<Filament, Long> {

    Optional<Filament> findFirstByFullIdAndColorHex(
            String fullId,
            String colorHex
    );

}
