package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "material_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

}
