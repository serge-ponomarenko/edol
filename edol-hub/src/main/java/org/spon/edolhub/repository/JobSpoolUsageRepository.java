package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobSpoolUsageRepository extends JpaRepository<JobSpoolUsage, Long> {

    List<JobSpoolUsage> findByPrintJobId(Long printJobId);

    List<JobSpoolUsage> findByFilamentSpoolId(Long filamentSpoolId);

}