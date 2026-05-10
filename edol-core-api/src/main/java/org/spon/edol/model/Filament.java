package org.spon.edol.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Filament {

    private int id;
    private String filamentBrandIndex;
    private String type;
    private String color;
    private String vendor;
    private String fullId;
    private double usedMeters;
    private double usedGrams;
    private boolean usedForObject;
    private boolean usedForSupport;
    private Integer amsSlot;

}
