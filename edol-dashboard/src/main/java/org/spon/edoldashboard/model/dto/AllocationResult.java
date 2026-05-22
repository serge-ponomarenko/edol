package org.spon.edoldashboard.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.spon.edoldashboard.model.entity.FilamentSpool;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AllocationResult {

    private FilamentSpool spool;
    private Integer allocatedGrams;
    private BigDecimal cost;

}
