package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "print_allocation_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintAllocationItem {

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
     * Allocation group reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private PrintAllocationGroup group;

    /**
     * Physical spool
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filament_spool_id")
    private FilamentSpool spool;

    /**
     * Allocated grams from spool
     */
    private Double allocatedGrams;

    /**
     * Estimated spool allocation cost
     */
    private BigDecimal estimatedCost;

    /**
     * User explicitly selected spool
     */
    private Boolean userSelected;

}
