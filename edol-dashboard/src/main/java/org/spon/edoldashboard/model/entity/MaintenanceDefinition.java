package org.spon.edoldashboard.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "maintenance_definition")
@Data
public class MaintenanceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 2000)
    private String description;

    private Integer intervalHours;

    private Integer intervalDays;

    private boolean active = true;
}
