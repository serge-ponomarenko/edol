package org.spon.edolhub.model.dto;

import lombok.Data;

@Data
public class AllocationItemDto {

    private Long spoolId;

    private String spoolName;

    private String colorHex;

    private Double spoolRemainingGrams;

    private Double allocatedGrams;

    private Double estimatedCost;

    private Boolean userSelected;

}
