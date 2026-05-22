package org.spon.edolhub.model.dto;

import lombok.Data;

@Data
public class AllocationResultDto {

    private Long spoolId;
    private Integer allocatedGrams;
    private Double cost;

}
