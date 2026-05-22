package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "filaments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: JAMG HE PETG Basic - comes from Printer
    private String fullId;

    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    private String printerFilamentProfileId;

    @ManyToOne
    @JoinColumn(name = "material_type_id")
    private MaterialType materialType;

    // Example: Basic, Matte, Silk
    private String brand;

    private String colorHex;

    // Example: 1.75 / 2.85
    private Double diameter;

    @Column(length = 2000)
    private String comment;

    public String getRgbColor() {
        if (colorHex == null || !colorHex.startsWith("#") || colorHex.length() != 7) {
            return "0,0,0";
        }

        int r = Integer.parseInt(colorHex.substring(1, 3), 16);
        int g = Integer.parseInt(colorHex.substring(3, 5), 16);
        int b = Integer.parseInt(colorHex.substring(5, 7), 16);

        return r + ", " + g + ", " + b;
    }

}
