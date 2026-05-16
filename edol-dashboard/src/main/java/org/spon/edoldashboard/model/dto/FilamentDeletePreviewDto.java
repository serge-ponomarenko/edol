package org.spon.edoldashboard.model.dto;

import java.util.List;

public record FilamentDeletePreviewDto(
        List<SpoolInfo> spools,
        List<JobInfo> jobs
) {

    public boolean hasDependencies() {
        return !spools.isEmpty() || !jobs.isEmpty();
    }

    public record SpoolInfo(
            Long id,
            Integer weightRemaining,
            String status
    ) {}

    public record JobInfo(
            Long id,
            String taskName,
            Double usedGrams
    ) {}
}