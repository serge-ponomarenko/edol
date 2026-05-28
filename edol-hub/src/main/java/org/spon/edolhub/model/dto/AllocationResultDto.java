package org.spon.edolhub.model.dto;

import lombok.Data;

@Data
public class AllocationResultDto {

    private Long spoolId;
    private Double allocatedGrams;
    private Double cost;

}
