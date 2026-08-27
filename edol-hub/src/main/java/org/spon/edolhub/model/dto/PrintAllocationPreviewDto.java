package org.spon.edolhub.model.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PrintAllocationPreviewDto {

    private UUID printJobId;

    private String jobStatus;

    private Boolean finalized;

    private Boolean reconciliationRequired;

    private List<AllocationGroupDto> groups;

}