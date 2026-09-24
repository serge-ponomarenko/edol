package org.spon.edolhub.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spon.edolhub.common.domain.PersistableEntity;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends PersistableEntity {

    @Column(nullable = false, updatable = false, length = 2048)
    private String issuer;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void initializeAuditTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateAuditTimestamp() {
        updatedAt = Instant.now();
    }
}
