package org.spon.edolhub.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private Tenant tenant;

    @Column
    private String name;

    private String website;

}
