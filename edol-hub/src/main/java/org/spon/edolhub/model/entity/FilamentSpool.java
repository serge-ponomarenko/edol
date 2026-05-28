package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "filament_spools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilamentSpool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logical filament (PLA, PETG, etc.)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_id")
    private Filament filament;

    /**
     * Total spool weight (grams)
     */
    private Double weightTotal;

    /**
     * Remaining weight (grams)
     */
    private Double weightRemaining;

    /**
     * Spool price
     */
    private BigDecimal price;

    /**
     * Purchase / store link
     */
    private String storeUrl;

    /**
     * When spool was bought
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime purchasedAt;

    /**
     * When spool was opened
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime openedAt;

    /**
     * When spool was last used
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime lastUsedAt;

    /**
     * When spool was last dried
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime lastDriedAt;

    /**
     * Spool status
     */
    @Enumerated(EnumType.STRING)
    private FilamentSpoolStatus status;

    /**
     * Comment
     */
    @Column(length = 2000)
    private String comment;

    public String getDisplayName() {
        return filament.getFullId();
    }

    public enum FilamentSpoolStatus {
        SEALED,
        ACTIVE,
        EMPTY,
        ARCHIVED
    }

    public String getFormattedOpenedTime() {
        return openedAt == null ? "-" : openedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

}

