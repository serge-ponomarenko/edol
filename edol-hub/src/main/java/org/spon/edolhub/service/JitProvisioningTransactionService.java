package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.model.entity.TenantMembershipRole;
import org.spon.edolhub.model.entity.TenantMembershipStatus;
import org.spon.edolhub.model.entity.User;
import org.spon.edolhub.repository.TenantMembershipRepository;
import org.spon.edolhub.repository.TenantRepository;
import org.spon.edolhub.repository.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class JitProvisioningTransactionService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    boolean userExists(OidcIdentity identity) {
        return userRepository.findByIssuerAndSubject(identity.issuer(), identity.subject()).isPresent();
    }

    @Transactional(readOnly = true)
    LegacyBootstrapState legacyBootstrapState() {
        return jdbcClient.sql("""
                        select exists(select 1 from hub.legacy_tenant_bootstrap_state) as present,
                               coalesce((select claim_open from hub.legacy_tenant_bootstrap_state where singleton), false) as open,
                               exists(select 1 from hub.legacy_tenant_bootstrap_state where claimed_at is not null) as claimed
                        """)
                .query((resultSet, rowNumber) -> new LegacyBootstrapState(
                        resultSet.getBoolean("present"),
                        resultSet.getBoolean("open"),
                        resultSet.getBoolean("claimed")
                ))
                .single();
    }

    @Transactional
    void claimLegacyTenant(OidcIdentity identity) {
        jdbcClient.sql("select hub.claim_legacy_tenant_owner(:issuer, :subject, :displayName, :email)")
                .param("issuer", identity.issuer())
                .param("subject", identity.subject())
                .param("displayName", identity.displayName())
                .param("email", identity.email())
                .query(UUID.class)
                .single();
    }

    @Transactional
    void createPersonalTenant(OidcIdentity identity, UUID tenantId) {
        User user = userRepository.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .orElseGet(() -> {
                    User created = new User();
                    created.setIssuer(identity.issuer());
                    created.setSubject(identity.subject());
                    created.setDisplayName(identity.displayName());
                    created.setEmail(identity.email());
                    return userRepository.save(created);
                });

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Personal tenant");
        tenant.setDefaultTenant(false);
        tenantRepository.save(tenant);

        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(TenantMembershipRole.OWNER);
        membership.setStatus(TenantMembershipStatus.ACTIVE);
        membershipRepository.save(membership);
    }

    record LegacyBootstrapState(boolean present, boolean open, boolean claimed) {
    }
}
