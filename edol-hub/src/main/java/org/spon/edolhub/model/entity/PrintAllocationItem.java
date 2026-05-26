package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    /**
     * Allocation group reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
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
    private Integer allocatedGrams;

    /**
     * Estimated spool allocation cost
     */
    private BigDecimal estimatedCost;

    /**
     * User explicitly selected spool
     */
    private Boolean userSelected;

}
