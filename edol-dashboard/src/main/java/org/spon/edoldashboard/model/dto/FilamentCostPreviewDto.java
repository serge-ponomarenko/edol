package org.spon.edoldashboard.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilamentCostPreviewDto {

    private Double totalCost;
    private List<AllocationResultDto> allocations;

}