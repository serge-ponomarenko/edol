package org.spon.edol.model;

import lombok.Data;


@Data
public class AmsSlot {

    private int id = 0;

    private String filamentType = "";

    private String filamentBrand = "";
    private String filamentBrandIndex = "";

    private String color = "";

    private Integer remaining = 0;

    private boolean active;

    public boolean isEmpty() {
        return filamentType.isEmpty() && filamentBrandIndex.isEmpty() && color.isEmpty();
    }
}
