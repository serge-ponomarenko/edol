package org.spon.edolhub.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spon.edolhub.common.domain.PersistableEntity;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends PersistableEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTenant;

}
