package org.spon.edolhub.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.spon.edolhub.model.entity.FilamentSpool;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AllocationResult {

    private FilamentSpool spool;
    private Double allocatedGrams;
    private BigDecimal cost;

}
