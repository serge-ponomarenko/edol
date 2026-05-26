package org.spon.edolhub.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrintAllocationPreviewDto {

    private Long printJobId;

    private String jobStatus;

    private Boolean finalized;

    private Boolean reconciliationRequired;

    private List<AllocationGroupDto> groups;

}