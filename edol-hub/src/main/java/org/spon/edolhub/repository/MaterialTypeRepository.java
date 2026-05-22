package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialTypeRepository extends JpaRepository<MaterialType, Long> {

    Optional<MaterialType> findByName(String name);

    Optional<MaterialType> findByNameIgnoreCase(String part);
}