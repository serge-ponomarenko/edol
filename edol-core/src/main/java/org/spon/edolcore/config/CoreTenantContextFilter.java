package org.spon.edolcore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Establishes Core tenant context from a validated Hub service request only. */
@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class CoreTenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-EDOL-Tenant-Id";

    private final CoreTenantContext tenantContext;
    private final String trustedHubClientId;

    public CoreTenantContextFilter(
            CoreTenantContext tenantContext,
            @Value("${edol-core.security.trusted-hub-client-id:edol-hub-service}") String trustedHubClientId
    ) {
        this.tenantContext = tenantContext;
        this.trustedHubClientId = trustedHubClientId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!trustedHubClientId.equals(jwtAuthentication.getToken().getClaimAsString("azp"))
                || !authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SCOPE_tenant.context"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Hub tenant context is not authorized");
            return;
        }

        List<String> tenantHeaders = java.util.Collections.list(request.getHeaders(TENANT_HEADER));
        if (tenantHeaders.size() != 1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Exactly one tenant context header is required");
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantHeaders.getFirst());
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant context header is invalid");
            return;
        }

        try (CoreTenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            filterChain.doFilter(request, response);
        }
    }
}
