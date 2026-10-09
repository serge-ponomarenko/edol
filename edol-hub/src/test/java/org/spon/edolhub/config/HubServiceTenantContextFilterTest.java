package org.spon.edolhub.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.TenantContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HubServiceTenantContextFilterTest {

    private final TenantContext tenantContext = new TenantContext();
    private final HubServiceTenantContextFilter filter = new HubServiceTenantContextFilter(tenantContext, "edol-ams-service");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void opensTenantContextOnlyForTrustedAmsService() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authentication("edol-ams-service"));
        MockHttpServletRequest request = request();
        request.addHeader("X-EDOL-Tenant-Id", tenantId.toString());

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                assertThat(tenantContext.getCurrentTenantId()).isEqualTo(tenantId));

        assertThat(tenantContext.hasCurrentTenant()).isFalse();
    }

    @Test
    void rejectsAmsJwtWithoutTenantContextScope() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-EDOL-Tenant-Id", UUID.randomUUID().toString());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("edol-ams-service")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/spools/find");
    }

    private JwtAuthenticationToken authentication(String clientId) {
        return new JwtAuthenticationToken(jwt(clientId), AuthorityUtils.createAuthorityList("SCOPE_tenant.context"));
    }

    private Jwt jwt(String clientId) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("azp", clientId));
    }
}
