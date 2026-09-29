package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.model.entity.TenantMembershipStatus;
import org.spon.edolhub.model.entity.User;
import org.spon.edolhub.repository.TenantMembershipRepository;
import org.spon.edolhub.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantMembershipService {

    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public List<TenantMembership> activeMemberships(OidcIdentity identity) {
        User user = userRepository.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
        if (user == null) {
            return List.of();
        }
        return membershipRepository.findAllByUserIdAndStatusOrderByCreatedAt(user.getId(), TenantMembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMembership(OidcIdentity identity, UUID tenantId) {
        return userRepository.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .flatMap(user -> membershipRepository.findByUserIdAndTenantIdAndStatus(
                        user.getId(), tenantId, TenantMembershipStatus.ACTIVE
                ))
                .isPresent();
    }
}
