package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilamentSpoolRepository extends
        JpaRepository<FilamentSpool, Long>,
        JpaSpecificationExecutor<FilamentSpool> {

    List<FilamentSpool> findByFilamentId(
            Long filamentId
    );

    Optional<FilamentSpool> findFirstByFilamentIdAndStatus(
            Long filamentId, FilamentSpool.FilamentSpoolStatus status
    );

    List<FilamentSpool> findAllByFilamentIdAndStatusIn(
            Long filamentId,
            List<FilamentSpool.FilamentSpoolStatus> statuses
    );

    void deleteAllByFilamentId(
            Long filamentId
    );

    List<FilamentSpool> findAllByFilamentIdAndStatus(
            Long filamentId,
            FilamentSpool.FilamentSpoolStatus status
    );

    @Query("""
                select s
                from FilamentSpool s
                join fetch s.filament f
                join fetch f.vendor
                join fetch f.materialType
            """)
    List<FilamentSpool> findAllWithFilament();

}
