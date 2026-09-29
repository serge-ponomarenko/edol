package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.OidcIdentity;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.TenantMembershipService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActiveTenantContextFilter extends OncePerRequestFilter {

    private final IdentityContext identityContext;
    private final TenantContext tenantContext;
    private final TenantMembershipService membershipService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/tenants/")
                || request.getRequestURI().startsWith("/oauth2/")
                || request.getRequestURI().startsWith("/login/")
                || request.getRequestURI().equals("/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser user)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        UUID tenantId = session == null ? null : sessionTenantId(session);
        if (tenantId == null) {
            response.sendRedirect("/tenants/select");
            return;
        }

        OidcIdentity identity = OidcIdentity.from(user);
        try (IdentityContext.IdentityScope identityScope = identityContext.open(identity.issuer(), identity.subject())) {
            if (!membershipService.hasActiveMembership(identity, tenantId)) {
                session.removeAttribute(HubSessionAttributes.ACTIVE_TENANT_ID);
                response.sendRedirect("/tenants/select");
                return;
            }
        }

        try (TenantContext.TenantScope tenantScope = tenantContext.open(tenantId)) {
            filterChain.doFilter(request, response);
        }
    }

    private UUID sessionTenantId(HttpSession session) {
        Object value = session.getAttribute(HubSessionAttributes.ACTIVE_TENANT_ID);
        if (value instanceof UUID tenantId) {
            return tenantId;
        }
        session.removeAttribute(HubSessionAttributes.ACTIVE_TENANT_ID);
        return null;
    }
}
