package org.spon.edolcore.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoreTrustedClientsJsonBindingTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void indexedJsonArrayFallsBackToHubOnlyWithValueInjection() {
        verifyClient("[\"edol-hub-service\",\"edol-ams-service\",\"edol-notify-service\"]",
                "edol-ams-service", true, 403);
    }

    @Test
    void csvJsonValueAllowsDeclaredClientsAndStillRequiresTrustAndScope() {
        String clients = "\"edol-hub-service,edol-ams-service,edol-notify-service\"";
        for (String client : List.of("edol-hub-service", "edol-ams-service", "edol-notify-service")) {
            verifyClient(clients, client, true, 200);
        }
        verifyClient(clients, "untrusted-service", true, 403);
        verifyClient(clients, "edol-ams-service", false, 403);
    }

    private void verifyClient(String jsonValue, String clientId, boolean tenantScope, int expectedStatus) {
        new ApplicationContextRunner()
                .withPropertyValues("edol.deployment.mode=secure-multi-tenant",
                        "spring.application.json={\"edol-core.security.trusted-service-client-ids\":" + jsonValue + "}")
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    new SpringApplicationJsonEnvironmentPostProcessor()
                            .postProcessEnvironment(context.getEnvironment(), new SpringApplication());
                })
                .withUserConfiguration(CoreTenantContext.class, CoreTenantContextFilter.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CoreTenantContext tenants = context.getBean(CoreTenantContext.class);
                    UUID tenantId = UUID.randomUUID();
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader("X-EDOL-Tenant-Id", tenantId.toString());
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    Jwt jwt = Jwt.withTokenValue("synthetic-token").header("alg", "none")
                            .claim("azp", clientId).build();
                    List<SimpleGrantedAuthority> authorities = tenantScope
                            ? List.of(new SimpleGrantedAuthority("SCOPE_tenant.context")) : List.of();
                    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
                    AtomicReference<UUID> observed = new AtomicReference<>();
                    context.getBean(CoreTenantContextFilter.class).doFilter(request, response,
                            (ignoredRequest, ignoredResponse) -> observed.set(tenants.getCurrentTenantId()));
                    assertThat(response.getStatus()).isEqualTo(expectedStatus);
                    assertThat(observed.get()).isEqualTo(expectedStatus == 200 ? tenantId : null);
                    assertThat(tenants.hasCurrentTenant()).isFalse();
                });
    }
}
