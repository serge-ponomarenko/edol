package org.spon.edolhub.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilamentCostPreviewDto {

    private Double totalCost;
    private List<AllocationResultDto> allocations;

}