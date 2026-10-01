package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.TenantMembershipService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveTenantContextFilterTest {

    private final IdentityContext identityContext = new IdentityContext();
    private final TenantContext tenantContext = new TenantContext();
    private final TenantMembershipService membershipService = mock(TenantMembershipService.class);
    private final ActiveTenantContextFilter filter = new ActiveTenantContextFilter(
            identityContext, tenantContext, membershipService
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void removesStaleTenantAndRedirectsWhenMembershipWasRevoked() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MockHttpSession session = sessionWithTenant(tenantId);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(authentication());
        when(membershipService.hasActiveMembership(any(), eq(tenantId))).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(session.getAttribute(HubSessionAttributes.ACTIVE_TENANT_ID)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/tenants/select");
        assertThat(identityContext.hasCurrentIdentity()).isFalse();
        assertThat(tenantContext.hasCurrentTenant()).isFalse();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void scopesAnActiveTenantOnlyForTheRequestAndClearsItAfterwards() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setSession(sessionWithTenant(tenantId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(authentication());
        when(membershipService.hasActiveMembership(any(), eq(tenantId))).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(identityContext.hasCurrentIdentity()).isFalse();
        assertThat(tenantContext.hasCurrentTenant()).isFalse();
    }

    private MockHttpSession sessionWithTenant(UUID tenantId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessionAttributes.ACTIVE_TENANT_ID, tenantId);
        return session;
    }

    private OAuth2AuthenticationToken authentication() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.ISS, "https://identity.example/realms/edol", IdTokenClaimNames.SUB, "subject-1")
        );
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "edol-keycloak");
    }
}
