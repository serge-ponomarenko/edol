package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "printers")
@Getter
@Setter
@NoArgsConstructor
public class Printer {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "display_id", unique = true)
    private String displayId;

    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "available_in_core", nullable = false)
    private boolean availableInCore;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

}
