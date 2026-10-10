package org.spon.edolams.model.terminal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terminals")
@Getter
@Setter
@NoArgsConstructor
public class AmsTerminal {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "allowed_printer_id", nullable = false, updatable = false)
    private UUID allowedPrinterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false)
    private TerminalLifecycle lifecycleState;

    @Column(name = "credential_digest")
    private byte[] credentialDigest;

    @Column(name = "credential_key_version")
    private Integer credentialKeyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "reset_at")
    private Instant resetAt;

    @Column(name = "replaced_by_terminal_id")
    private UUID replacedByTerminalId;
}
