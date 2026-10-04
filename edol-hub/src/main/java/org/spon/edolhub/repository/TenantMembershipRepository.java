package org.spon.edolhub.repository;

import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.model.entity.TenantMembershipStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    @EntityGraph(attributePaths = "tenant")
    List<TenantMembership> findAllByUserIdAndStatusOrderByCreatedAt(UUID userId, TenantMembershipStatus status);

    Optional<TenantMembership> findByUserIdAndTenantIdAndStatus(
            UUID userId,
            UUID tenantId,
            TenantMembershipStatus status
    );
}
