package org.spon.edoldashboard.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class FilamentReplacePreviewDto {

    private Long usagesCount;
    private Double totalGrams;
    private Long jobsCount;

    private List<JobUsageDto> jobs;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class JobUsageDto {
        private Long jobId;
        private String jobName;
        private Double usedGrams;
    }
}
