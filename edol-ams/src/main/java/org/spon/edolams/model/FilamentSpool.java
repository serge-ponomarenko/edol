package org.spon.edolams.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilamentSpool {

    private Long id;

    private Filament filament;

    private Integer weightTotal;

    private Integer weightRemaining;

    private BigDecimal price;

    private String storeUrl;

    private LocalDateTime openedAt;

    private LocalDateTime lastUsedAt;

    private LocalDateTime lastDriedAt;

    private FilamentSpoolStatus status;

    private String comment;

    public enum FilamentSpoolStatus {
        NEW,
        ACTIVE,
        EMPTY,
        ARCHIVED
    }

}

