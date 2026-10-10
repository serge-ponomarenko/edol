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
@Table(name = "terminal_pairings")
@Getter
@Setter
@NoArgsConstructor
public class TerminalPairing {

    @Id
    private UUID id;

    @Column(name = "terminal_id", nullable = false, updatable = false)
    private UUID terminalId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "allowed_printer_id", nullable = false, updatable = false)
    private UUID allowedPrinterId;

    @Column(name = "code_digest", nullable = false, updatable = false)
    private byte[] codeDigest;

    @Column(name = "code_key_version", nullable = false, updatable = false)
    private int codeKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PairingState state;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
