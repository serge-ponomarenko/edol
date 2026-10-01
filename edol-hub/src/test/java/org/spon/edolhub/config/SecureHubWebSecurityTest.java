package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.controller.TenantSelectionController;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.JitProvisioningService;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.TenantMembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TenantSelectionController.class,
        properties = "edol.deployment.mode=secure-multi-tenant",
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class
)
@Import({
        SecurityConfig.class,
        OidcLoginSuccessHandler.class,
        HubSessionExpiryFilter.class,
        ActiveTenantContextFilter.class,
        AmsCompatibilityIngressFilter.class,
        IdentityContext.class,
        TenantContext.class,
        SecureHubWebSecurityTest.OAuthClientTestConfiguration.class
})
class SecureHubWebSecurityTest {

    private static final String ISSUER = "https://identity.example/realms/edol";
    private static final String SUBJECT = "subject-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityFilterChain secureSecurityFilterChain;

    @MockitoBean
    private TenantMembershipService membershipService;

    @MockitoBean
    private JitProvisioningService provisioningService;

    @MockitoBean
    private PrinterAccessService printerAccessService;

    @Test
    void startsAuthorizationCodeLoginWithStateAndPkce() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/edol-keycloak"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://identity.example/authorize?")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")));
    }

    @Test
    void rejectsTenantSelectionWithoutCsrfToken() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/tenants/select")
                        .param("tenantId", tenantId.toString())
                        .session(authenticatedSession()))
                .andExpect(status().isForbidden());

        verify(membershipService, never()).hasActiveMembership(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storesOnlyMembershipValidatedTenantInServerSession() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(membershipService.hasActiveMembership(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(tenantId)))
                .thenReturn(true);
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/tenants/select")
                        .param("tenantId", tenantId.toString())
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(membershipService).hasActiveMembership(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(tenantId));
        org.assertj.core.api.Assertions.assertThat(session.getAttribute(HubSessionAttributes.ACTIVE_TENANT_ID))
                .isEqualTo(tenantId);
    }

    @Test
    void postsLogoutOnlyWithCsrfAndRedirectsAwayFromHub() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(authenticatedSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/")));
    }

    @Test
    void changesTheServerSessionIdWhenOidcAuthenticationSucceeds() {
        OAuth2LoginAuthenticationFilter loginFilter = secureSecurityFilterChain.getFilters().stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();
        Object strategy = ReflectionTestUtils.getField(loginFilter, "sessionStrategy");
        org.assertj.core.api.Assertions.assertThat(strategy).isInstanceOf(CompositeSessionAuthenticationStrategy.class);
        java.util.List<?> delegates = (java.util.List<?>) ReflectionTestUtils.getField(strategy, "delegateStrategies");
        org.assertj.core.api.Assertions.assertThat(delegates)
                .anyMatch(ChangeSessionIdAuthenticationStrategy.class::isInstance);

        MockHttpSession session = new MockHttpSession();
        String originalId = session.getId();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);

        ((SessionAuthenticationStrategy) strategy).onAuthentication(
                new OAuth2AuthenticationToken(oidcUser(), oidcUser().getAuthorities(), "edol-keycloak"),
                request,
                new org.springframework.mock.web.MockHttpServletResponse()
        );

        org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(originalId);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new OAuth2AuthenticationToken(oidcUser(), oidcUser().getAuthorities(), "edol-keycloak"))
        );
        return session;
    }

    private OidcUser oidcUser() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, SUBJECT)
        );
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class OAuthClientTestConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("edol-keycloak")
                    .clientId("edol-hub-web")
                    .clientSecret("test-only-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://identity.example/authorize")
                    .tokenUri("https://identity.example/token")
                    .jwkSetUri("https://identity.example/jwks")
                    .userInfoUri("https://identity.example/userinfo")
                    .userNameAttributeName("sub")
                    .clientName("EDOL Keycloak")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
