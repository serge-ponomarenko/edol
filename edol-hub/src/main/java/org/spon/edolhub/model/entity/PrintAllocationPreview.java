package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "print_allocation_preview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintAllocationPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Print job reference
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_job_id")
    private PrintJob printJob;

    /**
     * Allocation finalized and inventory mutated
     */
    private Boolean finalized;

    @OneToMany(
            mappedBy = "preview",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PrintAllocationGroup> groups;

}