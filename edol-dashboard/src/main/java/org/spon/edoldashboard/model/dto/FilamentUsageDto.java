package org.spon.edoldashboard.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilamentUsageDto {

    private String vendor;
    private String material;
    private String brand;
    private String colorHex;

    private double usedGrams;
    private double cost;

    private List<SpoolUsageDto> spoolUsages;

}
