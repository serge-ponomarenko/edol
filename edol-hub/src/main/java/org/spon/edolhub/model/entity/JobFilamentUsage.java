package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "job_filament_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobFilamentUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Print job reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_job_id")
    private PrintJob printJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_id")
    private Filament filament;

    /**
     * Used filament grams
     */
    private Double usedGrams;

    /**
     * Used filament meters
     */
    private Double usedMeters;

    /**
     * Calculated cost
     */
    private BigDecimal cost;

    /**
     * Used for object
     */
    private boolean usedForObject;

    /**
     * Used for support
     */
    private boolean usedForSupport;

}
