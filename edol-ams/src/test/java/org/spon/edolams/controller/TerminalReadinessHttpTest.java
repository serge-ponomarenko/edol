package org.spon.edolams.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolams.config.AmsHubServiceTenantContextFilter;
import org.spon.edolams.config.SecurityConfig;
import org.spon.edolams.service.AmsTenantContext;
import org.spon.edolams.service.TerminalAuthenticationService;
import org.spon.edolams.service.TerminalCredentialCodec;
import org.spon.edolams.service.TerminalOperationalService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TerminalReadinessHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant").applyTo(context);
        context.register(WebConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        clearInvocations(context.getBean(JdbcTemplate.class), context.getBean(TerminalCredentialCodec.class),
                context.getBean(TerminalOperationalService.class), context.getBean(JwtDecoder.class));
    }

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void missingAuthorizationIsBadRequestRatherThanTerminalAuthenticationRejection() throws Exception {
        mvc.perform(get("/api/terminal/v1/state"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(MissingRequestHeaderException.class));
        verifyNoTerminalSideEffects();
    }

    @Test
    void malformedReadinessCredentialReachesRealAuthenticationAndIsRejectedWithoutDatabaseAccess() throws Exception {
        mvc.perform(get("/api/terminal/v1/state")
                        .header("Authorization", "EDOL-Terminal readiness-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "EDOL-Terminal"));
        verifyNoTerminalSideEffects();
    }

    private void verifyNoTerminalSideEffects() {
        verifyNoInteractions(context.getBean(JdbcTemplate.class), context.getBean(TerminalCredentialCodec.class),
                context.getBean(TerminalOperationalService.class), context.getBean(JwtDecoder.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, TerminalOperationalController.class, AmsApiExceptionHandler.class,
            TerminalAuthenticationService.class, AmsTenantContext.class})
    static class WebConfig {
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean TerminalCredentialCodec codec() { return mock(TerminalCredentialCodec.class); }
        @Bean TerminalOperationalService operations() { return mock(TerminalOperationalService.class); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean AmsHubServiceTenantContextFilter tenantFilter(AmsTenantContext tenants) {
            return new AmsHubServiceTenantContextFilter(tenants, "edol-hub-service");
        }
    }
}
