package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.spon.edolhub.service.TenantContext;
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

/** Establishes Hub tenant context for the exact AMS service ingress routes. */
@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class HubServiceTenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-EDOL-Tenant-Id";

    private final TenantContext tenantContext;
    private final String trustedAmsClientId;

    public HubServiceTenantContextFilter(
            TenantContext tenantContext,
            @Value("${edol-hub.security.trusted-ams-client-id:edol-ams-service}") String trustedAmsClientId
    ) {
        this.tenantContext = tenantContext;
        this.trustedAmsClientId = trustedAmsClientId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("GET".equals(request.getMethod())
                && ("/api/spools/find-by-id".equals(path) || "/api/spools/find".equals(path)))
                && !("POST".equals(request.getMethod()) && path.matches("/s/[^/]+/[^/]+/[^/]+"));
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

        if (!trustedAmsClientId.equals(jwtAuthentication.getToken().getClaimAsString("azp"))
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("SCOPE_tenant.context"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "AMS tenant context is not authorized");
            return;
        }

        List<String> tenantHeaders = java.util.Collections.list(request.getHeaders(TENANT_HEADER));
        if (tenantHeaders.size() != 1) {
            badRequest(response, "Exactly one tenant context header is required");
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantHeaders.getFirst());
        } catch (IllegalArgumentException exception) {
            badRequest(response, "Tenant context header is invalid");
            return;
        }

        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            filterChain.doFilter(request, response);
        }
    }

    private void badRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain");
        response.getWriter().write(message);
    }
}
