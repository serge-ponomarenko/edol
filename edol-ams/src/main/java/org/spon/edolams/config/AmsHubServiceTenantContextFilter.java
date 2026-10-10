package org.spon.edolams.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.spon.edolams.service.AmsTenantContext;
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

@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class AmsHubServiceTenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-EDOL-Tenant-Id";
    private final AmsTenantContext tenantContext;
    private final String trustedHubClientId;

    public AmsHubServiceTenantContextFilter(
            AmsTenantContext tenantContext,
            @Value("${edol-ams.security.trusted-hub-client-id:edol-hub-service}") String trustedHubClientId
    ) {
        this.tenantContext = tenantContext;
        this.trustedHubClientId = trustedHubClientId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/terminals/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !trustedHubClientId.equals(jwt.getToken().getClaimAsString("azp"))
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_tenant.context"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Hub service tenant context is not authorized");
            return;
        }
        List<String> values = java.util.Collections.list(request.getHeaders(TENANT_HEADER));
        if (values.size() != 1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Exactly one tenant context header is required");
            return;
        }
        try {
            UUID tenantId = UUID.fromString(values.getFirst());
            try (AmsTenantContext.Scope ignored = tenantContext.open(tenantId)) {
                chain.doFilter(request, response);
            }
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant context header is invalid");
        }
    }
}
