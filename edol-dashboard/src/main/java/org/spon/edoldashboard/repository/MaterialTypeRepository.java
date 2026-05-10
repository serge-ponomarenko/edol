package org.spon.edoldashboard.repository;

import org.spon.edoldashboard.model.entity.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialTypeRepository extends JpaRepository<MaterialType, Long> {

    Optional<MaterialType> findByName(String name);

    Optional<MaterialType> findByNameIgnoreCase(String part);
}