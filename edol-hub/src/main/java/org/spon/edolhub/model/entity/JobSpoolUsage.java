package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private Tenant tenant;

    /**
     * Print job reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_job_id", nullable = false)
    private PrintJob printJob;

    /**
     * Physical spool used for printing
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_spool_id", nullable = false)
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
