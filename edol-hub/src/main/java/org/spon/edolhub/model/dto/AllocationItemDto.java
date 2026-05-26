package org.spon.edolhub.model.dto;

import lombok.Data;

@Data
public class AllocationItemDto {

    private Long spoolId;

    private String spoolName;

    private String colorHex;

    private Integer allocatedGrams;

    private Double estimatedCost;

    private Boolean userSelected;

}
