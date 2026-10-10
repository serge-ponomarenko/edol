package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantOwnerAuthorizationService {

    private final IdentityContext identityContext;
    private final TenantMembershipService membershipService;
    private final TenantContext tenantContext;

    public void requireCurrentOwner() {
        OAuth2AuthenticationToken token = authenticatedToken();
        OidcIdentity identity = OidcIdentity.from((OidcUser) token.getPrincipal());
        UUID tenantId = tenantContext.getCurrentTenantId();
        try (IdentityContext.IdentityScope ignored = identityContext.open(identity.issuer(), identity.subject())) {
            if (!membershipService.hasActiveOwnerMembership(identity, tenantId)) {
                throw new AccessDeniedException("Owner membership is required for terminal management");
            }
        }
    }

    private OAuth2AuthenticationToken authenticatedToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser) {
            return token;
        }
        throw new AccessDeniedException("An authenticated owner session is required for terminal management");
    }
}
