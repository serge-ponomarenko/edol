package org.spon.edolcore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

final class AgentCredentialFilter extends OncePerRequestFilter {
    private final AgentPairingService agents;
    private final CoreTenantContext tenants;

    AgentCredentialFilter(AgentPairingService agents, CoreTenantContext tenants) {
        this.agents = agents;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!request.isSecure()) {
            response.setStatus(403);
            return;
        }
        if (request.getHeader("X-EDOL-Tenant-Id") != null || request.getHeader("X-Agent-Id") != null) {
            response.setStatus(400);
            return;
        }
        if ("POST".equals(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + "/api/agent/v1/enroll")) {
            chain.doFilter(request, response);
            return;
        }
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1 || !headers.getFirst().startsWith("EdolAgent ")) {
            response.setStatus(401);
            return;
        }
        AgentPairingService.Identity identity;
        try {
            identity = agents.authenticate(headers.getFirst().substring("EdolAgent ".length()));
        } catch (ResponseStatusException exception) {
            response.setStatus(exception.getStatusCode().value());
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null,
                List.of(new SimpleGrantedAuthority("AGENT_UPLOAD"))));
        SecurityContextHolder.setContext(context);
        try (var ignored = tenants.open(identity.tenantId())) {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
