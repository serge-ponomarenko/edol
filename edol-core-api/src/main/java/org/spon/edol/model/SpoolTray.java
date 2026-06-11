package org.spon.edol.model;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class SpoolTray {

    String filamentType = "";

    String filamentBrand = "";
    String filamentBrandIndex = "";

    String color = "";

    Integer remaining = 0;

}
