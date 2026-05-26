package org.spon.edolhub.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AllocationSpoolOptionDto {

    private Long id;

    private String name;

    private String status;

    private Integer weightTotal;

    private Integer weightRemaining;

    private BigDecimal price;

}
