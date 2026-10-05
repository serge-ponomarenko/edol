package org.spon.edolcore.persistence.printer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Stores the successful result of a Hub provisioning request for a safe retry. */
@Entity
@Table(name = "printer_provisioning_requests")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrinterProvisioningRequest {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_id", nullable = false, unique = true)
    private Printer printer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
