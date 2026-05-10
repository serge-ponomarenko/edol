package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.JobFilamentUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobFilamentUsageRepository extends JpaRepository<JobFilamentUsage, Long> {

    List<JobFilamentUsage> findByPrintJobId(Long printJobId);

    List<JobFilamentUsage> findByFilamentId(Long filamentId);

    List<JobFilamentUsage> findByFilamentIdAndPrintJobIdIn(Long filamentId, List<Long> jobIds);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE JobFilamentUsage u
                SET u.filament.id = :targetId
                WHERE u.filament.id = :sourceId
            """)
    int moveUsages(Long sourceId, Long targetId);

}
