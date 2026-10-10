package org.spon.edolcore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Temporary ingress for the exact pre-service-auth Notify and AMS Core routes.
 * It is independent from browser and service token tenant propagation.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "edol-core.legacy-compatibility.enabled",
        havingValue = "true"
)
class CoreLegacyCompatibilityFilter extends OncePerRequestFilter {

    static final String LEGACY_AUTHORITY = "ROLE_EDOL_CORE_LEGACY_COMPATIBILITY";

    private final CoreLegacyCompatibilityProperties properties;
    private final CoreTenantContext tenantContext;
    private final JdbcClient catalogJdbc;
    private final Counter acceptedRequests;

    CoreLegacyCompatibilityFilter(
            CoreLegacyCompatibilityProperties properties,
            CoreTenantContext tenantContext,
            @Qualifier("coreCatalogDataSource") DataSource catalogDataSource,
            org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistry
    ) {
        this.properties = properties;
        this.tenantContext = tenantContext;
        this.catalogJdbc = JdbcClient.create(catalogDataSource);
        MeterRegistry registry = meterRegistry.getIfAvailable();
        this.acceptedRequests = registry == null
                ? null
                : Counter.builder("edol.core.legacy_compatibility.requests")
                .description("Accepted temporary legacy Core compatibility requests")
                .register(registry);
        if (acceptedRequests != null) {
            log.info("Core legacy compatibility usage metric initialized: name={}, uses={}",
                    acceptedRequests.getId().getName(), acceptedRequests.count());
        }
        if (properties.legacyTenantId() == null || properties.allowedCidrs().isEmpty()) {
            throw new IllegalStateException("Legacy Core compatibility requires a tenant and source CIDRs");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader("Authorization") != null || !matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isAllowedSource(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Legacy Core compatibility source is not allowed");
            return;
        }

        UUID printerId = printerId(request.getRequestURI());
        if (printerId != null && !ownsLegacyPrinter(printerId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (acceptedRequests != null) {
            acceptedRequests.increment();
        }
        log.warn("Accepted temporary legacy Core compatibility request: {} {} from {} (uses={})",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr(),
                acceptedRequests == null ? "unavailable" : acceptedRequests.count());

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext legacyContext = SecurityContextHolder.createEmptyContext();
        legacyContext.setAuthentication(new PreAuthenticatedAuthenticationToken(
                "legacy-core-client",
                "N/A",
                List.of(new SimpleGrantedAuthority(LEGACY_AUTHORITY))
        ));
        SecurityContextHolder.setContext(legacyContext);
        try (CoreTenantContext.TenantScope ignored = tenantContext.open(properties.legacyTenantId())) {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private boolean isAllowedSource(String remoteAddress) {
        return properties.allowedCidrs().stream()
                .map(IpAddressMatcher::new)
                .anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private boolean ownsLegacyPrinter(UUID printerId) {
        return catalogJdbc.sql("select tenant_id from core.printers where id = :printerId")
                .param("printerId", printerId)
                .query(UUID.class)
                .optional()
                .filter(properties.legacyTenantId()::equals)
                .isPresent();
    }

    private UUID printerId(String requestUri) {
        String[] segments = requestUri.split("/");
        if (segments.length < 4 || !"api".equals(segments[1]) || !"printers".equals(segments[2])) {
            return null;
        }
        try {
            return UUID.fromString(segments[3]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) && ("/api/printers".equals(path) || "/api/printers/state".equals(path))) {
            return true;
        }
        if (path.matches("/api/printers/[0-9a-fA-F-]+/state")
                || path.matches("/api/printers/[0-9a-fA-F-]+/camera/status-image")) {
            return "GET".equals(method);
        }
        return "POST".equals(method) && path.matches(
                "/api/printers/[0-9a-fA-F-]+/commands/(stop|resume|pause|fetchmetadata|pushall)"
        );
    }
}
