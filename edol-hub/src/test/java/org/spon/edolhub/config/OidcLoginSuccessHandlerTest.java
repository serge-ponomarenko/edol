package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.service.JitProvisioningService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcLoginSuccessHandlerTest {

    @Mock
    private JitProvisioningService provisioningService;

    @InjectMocks
    private OidcLoginSuccessHandler handler;

    @Test
    void callbackAutoSelectsOneProvisionedMembershipAndRecordsAuthenticationTime() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(provisioningService.provisionOrLoad(any())).thenReturn(List.of(membership(tenantId)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(request.getSession(false).getAttribute(HubSessionAttributes.ACTIVE_TENANT_ID)).isEqualTo(tenantId);
        assertThat(request.getSession(false).getAttribute(HubSessionAttributes.AUTHENTICATED_AT)).isInstanceOf(Long.class);
    }

    @Test
    void callbackRequiresExplicitSelectionWhenMultipleMembershipsExist() throws Exception {
        when(provisioningService.provisionOrLoad(any())).thenReturn(List.of(
                membership(UUID.randomUUID()), membership(UUID.randomUUID())
        ));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(HubSessionAttributes.ACTIVE_TENANT_ID, UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/tenants/select");
        assertThat(request.getSession(false).getAttribute(HubSessionAttributes.ACTIVE_TENANT_ID)).isNull();
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

    private TenantMembership membership(UUID tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        return membership;
    }
}
