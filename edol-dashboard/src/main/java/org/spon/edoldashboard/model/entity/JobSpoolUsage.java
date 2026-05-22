package org.spon.edoldashboard.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_spool_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSpoolUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Print job reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_job_id")
    private PrintJob printJob;

    /**
     * Physical spool used for printing
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_spool_id")
    private FilamentSpool filamentSpool;

    /**
     * Actual consumed grams from this spool
     */
    private Double usedGrams;

    /**
     * Actual cost of consumed filament
     */
    private BigDecimal cost;

    /**
     * Runtime creation timestamp
     */
    private LocalDateTime createdAt;

}