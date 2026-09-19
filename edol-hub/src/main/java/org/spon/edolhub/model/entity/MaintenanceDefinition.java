package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "maintenance_definition")
@Data
public class MaintenanceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    private String name;

    @Column(length = 2000)
    private String description;

    private Integer intervalHours;

    private Integer intervalDays;

    private boolean active = true;
}
