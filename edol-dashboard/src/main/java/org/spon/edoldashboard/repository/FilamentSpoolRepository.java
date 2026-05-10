package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilamentSpoolRepository extends
        JpaRepository<FilamentSpool, Long>,
        JpaSpecificationExecutor<FilamentSpool> {

    List<FilamentSpool> findByFilamentId(Long filamentId);

    List<FilamentSpool> findByStatus(FilamentSpool.FilamentSpoolStatus status);

    Optional<FilamentSpool> findFirstByFilamentIdAndStatus(Long filamentId, FilamentSpool.FilamentSpoolStatus status);

    void deleteAllByFilamentId(Long filamentId);

}
