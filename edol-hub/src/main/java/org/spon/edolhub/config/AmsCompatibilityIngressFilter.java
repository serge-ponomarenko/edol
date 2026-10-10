package org.spon.edolhub.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolhub.service.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * The reverse proxy injects the assertion only on its private AMS ingress and
 * strips it from every public request. This is a temporary Stage 4 boundary.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class AmsCompatibilityIngressFilter extends OncePerRequestFilter implements RequestMatcher {

    private static final String INGRESS_HEADER = "X-EDOL-AMS-INGRESS";

    private final TenantContext tenantContext;
    private final Counter acceptedRequests;

    @Value("${edol-hub.ams-compatibility.legacy-tenant-id:}")
    private String legacyTenantId;

    @Value("${edol-hub.ams-compatibility.ingress-token:}")
    private String ingressToken;

    public AmsCompatibilityIngressFilter(TenantContext tenantContext, MeterRegistry meterRegistry) {
        this.tenantContext = tenantContext;
        this.acceptedRequests = Counter.builder("edol.hub.legacy_tenant_compatibility.uses")
                .description("Accepted temporary Hub AMS compatibility ingress requests")
                .register(meterRegistry);
        log.info("Hub AMS compatibility usage metric initialized: name={}, uses={}",
                acceptedRequests.getId().getName(), acceptedRequests.count());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAllowedRoute(request);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return isAllowedRoute(request) && isTrustedIngress(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isTrustedIngress(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(legacyTenantId);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "AMS compatibility ingress is unavailable");
            return;
        }

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext internalContext = SecurityContextHolder.createEmptyContext();
        internalContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "ams-compatibility", "N/A", AuthorityUtils.createAuthorityList("ROLE_AMS_COMPATIBILITY")
        ));
        SecurityContextHolder.setContext(internalContext);
        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            acceptedRequests.increment();
            log.warn("Accepted temporary Hub AMS compatibility ingress request: {} {} (uses={})",
                    request.getMethod(), request.getRequestURI(), acceptedRequests.count());
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private boolean isTrustedIngress(HttpServletRequest request) {
        return ingressToken != null && !ingressToken.isBlank()
                && ingressToken.equals(request.getHeader(INGRESS_HEADER));
    }

    private boolean isAllowedRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("GET".equals(request.getMethod())) {
            return "/api/spools/find-by-id".equals(path) || "/api/spools/find".equals(path);
        }
        return "POST".equals(request.getMethod()) && path.matches("/s/[^/]+/[^/]+/[^/]+");
    }
}
