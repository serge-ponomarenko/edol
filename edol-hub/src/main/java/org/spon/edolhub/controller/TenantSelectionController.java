package org.spon.edolhub.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.config.HubSessionAttributes;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.OidcIdentity;
import org.spon.edolhub.service.TenantMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantSelectionController {

    private final IdentityContext identityContext;
    private final TenantMembershipService membershipService;

    @GetMapping("/select")
    public String select(Authentication authentication, Model model) {
        OidcIdentity identity = identity(authentication);
        try (IdentityContext.IdentityScope ignored = identityContext.open(identity.issuer(), identity.subject())) {
            List<TenantMembership> memberships = membershipService.activeMemberships(identity);
            model.addAttribute("memberships", memberships);
            return "tenants/select";
        }
    }

    @PostMapping("/select")
    public String selectTenant(
            Authentication authentication,
            @RequestParam UUID tenantId,
            HttpServletRequest request
    ) {
        OidcIdentity identity = identity(authentication);
        try (IdentityContext.IdentityScope ignored = identityContext.open(identity.issuer(), identity.subject())) {
            if (!membershipService.hasActiveMembership(identity, tenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active tenant membership is required");
            }
        }
        request.getSession().setAttribute(HubSessionAttributes.ACTIVE_TENANT_ID, tenantId);
        return "redirect:/";
    }

    private OidcIdentity identity(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OIDC authentication is required");
        }
        return OidcIdentity.from(user);
    }
}
