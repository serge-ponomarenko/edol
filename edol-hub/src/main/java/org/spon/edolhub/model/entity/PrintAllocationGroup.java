package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "print_allocation_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintAllocationGroup {

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
     * Preview reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preview_id", nullable = false)
    private PrintAllocationPreview preview;

    /**
     * Logical filament
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filament_id")
    private Filament filament;

    /**
     * Allocation state
     */
    @Enumerated(EnumType.STRING)
    private AllocationStatus status;

    /**
     * Total requested grams
     */
    private Double requestedGrams;

    /**
     * Successfully allocated grams
     */
    private Double allocatedGrams;

    /**
     * Missing grams
     */
    private Double missingGrams;

    /**
     * User manually changed this allocation
     */
    private Boolean userOverridden;

    /**
     * Runtime AMS slot binding
     */
    private Integer amsSlot;

    @OneToMany(
            mappedBy = "group",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PrintAllocationItem> items;

}
