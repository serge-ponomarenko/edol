package org.spon.edolhub.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AllocationSpoolOptionDto {

    private Long id;

    private String name;

    private String status;

    private Double weightTotal;

    private Double weightRemaining;

    private BigDecimal price;

}
