package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.JobSpoolUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobSpoolUsageRepository extends JpaRepository<JobSpoolUsage, Long> {

    List<JobSpoolUsage> findByPrintJobId(Long printJobId);

    List<JobSpoolUsage> findByFilamentSpoolId(Long filamentSpoolId);

}