package org.spon.edolcore.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoreTenantContextFilterTest {

    private final CoreTenantContext tenantContext = new CoreTenantContext();
    private final CoreTenantContextFilter filter = new CoreTenantContextFilter(
            tenantContext,
            List.of("edol-hub-service", "edol-notify-service", "edol-ams-service")
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void opensTenantContextForTrustedHubToken() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-EDOL-Tenant-Id", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(trustedHubAuthentication());
        AtomicReference<UUID> observedTenant = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observedTenant.set(tenantContext.getCurrentTenantId()));

        assertThat(observedTenant.get()).isEqualTo(tenantId);
        assertThat(tenantContext.hasCurrentTenant()).isFalse();
    }

    @Test
    void rejectsMissingTenantHeaderForTrustedHubToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(trustedHubAuthentication());

        filter.doFilter(new MockHttpServletRequest(), response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Filter chain must not run");
        });

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void opensTenantContextForTrustedAmsToken() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-EDOL-Tenant-Id", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(authentication("edol-ams-service"));
        AtomicReference<UUID> observedTenant = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observedTenant.set(tenantContext.getCurrentTenantId()));

        assertThat(observedTenant.get()).isEqualTo(tenantId);
        assertThat(tenantContext.hasCurrentTenant()).isFalse();
    }

    @Test
    void rejectsTenantContextFromAnUntrustedServiceClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-EDOL-Tenant-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(authentication("untrusted-service"));

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Filter chain must not run");
        });

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private JwtAuthenticationToken trustedHubAuthentication() {
        return authentication("edol-hub-service");
    }

    private JwtAuthenticationToken authentication(String clientId) {
        Jwt token = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("azp", clientId)
        );
        return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("SCOPE_tenant.context")));
    }
}
