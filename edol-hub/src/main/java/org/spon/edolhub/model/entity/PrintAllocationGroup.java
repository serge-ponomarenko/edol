package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

    /**
     * Preview reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preview_id")
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
    private Integer requestedGrams;

    /**
     * Successfully allocated grams
     */
    private Integer allocatedGrams;

    /**
     * Missing grams
     */
    private Integer missingGrams;

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
