package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.FilamentSpool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    List<FilamentSpool> findAllByFilamentIdAndFilamentTenantIdAndStatusIn(
            Long filamentId,
            UUID tenantId,
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

    @Query("""
            select spool
            from FilamentSpool spool
            join fetch spool.filament filament
            join fetch filament.vendor
            join fetch filament.materialType
            where filament.tenantId = :tenantId
              and (:vendor is null or filament.vendor.name = :vendor)
              and (:material is null or filament.materialType.name = :material)
              and spool.status in :statuses
            """)
    List<FilamentSpool> findAllByTenantIdAndFiltersWithDetails(
            @Param("tenantId") UUID tenantId,
            @Param("vendor") String vendor,
            @Param("material") String material,
            @Param("statuses") List<FilamentSpool.FilamentSpoolStatus> statuses
    );

    List<FilamentSpool> findAllByFilamentTenantId(UUID tenantId);

    Optional<FilamentSpool> findByIdAndFilamentTenantId(Long id, UUID tenantId);

}
