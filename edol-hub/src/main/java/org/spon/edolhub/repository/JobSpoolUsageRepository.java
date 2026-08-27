package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobSpoolUsageRepository extends JpaRepository<JobSpoolUsage, Long> {

    List<JobSpoolUsage> findByPrintJobId(UUID printJobId);

    List<JobSpoolUsage> findByFilamentSpoolId(Long filamentSpoolId);

}