package org.spon.edolams.model;

import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filament {

    private Long id;

    private String fullId;

    private Vendor vendor;

    private String printerFilamentProfileId;

    private MaterialType materialType;

    private String brand;

    private String colorHex;

    private Double diameter;

    private String comment;

}
