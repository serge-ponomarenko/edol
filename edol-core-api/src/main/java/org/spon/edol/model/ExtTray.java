package org.spon.edol.model;

import lombok.Data;


@Data
public class ExtTray {

    private String filamentType = "";

    private String filamentBrand = "";
    private String filamentBrandIndex = "";

    private String color = "";

    private Integer remaining = 0;

}
