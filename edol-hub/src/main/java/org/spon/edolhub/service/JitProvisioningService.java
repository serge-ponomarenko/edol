package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.TenantMembership;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JitProvisioningService {

    private final IdentityContext identityContext;
    private final TenantContext tenantContext;
    private final TenantMembershipService membershipService;
    private final JitProvisioningTransactionService transactionService;

    @Value("${edol-hub.registration.self-service-enabled:false}")
    private boolean selfServiceEnabled;

    public List<TenantMembership> provisionOrLoad(OidcIdentity identity) {
        try (IdentityContext.IdentityScope ignored = identityContext.open(identity.issuer(), identity.subject())) {
            List<TenantMembership> existing = membershipService.activeMemberships(identity);
            if (!existing.isEmpty()) {
                return existing;
            }
            if (transactionService.userExists(identity)) {
                if (!selfServiceEnabled) {
                    return List.of();
                }
                return createPersonalTenant(identity);
            }
            JitProvisioningTransactionService.LegacyBootstrapState bootstrapState =
                    transactionService.legacyBootstrapState();
            if (bootstrapState.open()) {
                transactionService.claimLegacyTenant(identity);
                return membershipService.activeMemberships(identity);
            }
            if (bootstrapState.present() && !bootstrapState.claimed()) {
                return List.of();
            }
            if (!selfServiceEnabled) {
                return List.of();
            }
            return createPersonalTenant(identity);
        }
    }

    public List<TenantMembership> createPersonalTenant(OidcIdentity identity) {
        UUID tenantId = UUID.randomUUID();
        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            transactionService.createPersonalTenant(identity, tenantId);
        }
        return membershipService.activeMemberships(identity);
    }

}
