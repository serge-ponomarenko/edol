package org.spon.edolhub.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class PersistableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

}
