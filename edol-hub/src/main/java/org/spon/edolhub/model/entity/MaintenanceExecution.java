package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_execution")
@Data
public class MaintenanceExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private MaintenanceDefinition maintenance;

    private LocalDateTime executedAt;

    private Integer printerTotalHours;

    private String notes;
}