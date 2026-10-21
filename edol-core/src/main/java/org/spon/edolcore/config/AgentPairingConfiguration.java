package org.spon.edolcore.config;

import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.agent.pairing.AgentEnrollmentLimiter;
import org.spon.edolcore.service.agent.pairing.AgentPairingProperties;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false}")
@EnableConfigurationProperties(AgentPairingProperties.class)
public class AgentPairingConfiguration {
    @Bean
    AgentCredentialCodec agentCredentialCodec(AgentPairingProperties properties) {
        return new AgentCredentialCodec(properties);
    }

    @Bean
    AgentPairingService agentPairingService(JdbcTemplate jdbc, CoreTenantContext tenants, AgentCredentialCodec codec) {
        return new AgentPairingService(jdbc, tenants, codec);
    }

    @Bean
    AgentEnrollmentLimiter agentEnrollmentLimiter() {
        return new AgentEnrollmentLimiter(Clock.systemUTC());
    }

    @Bean
    @Order(1)
    SecurityFilterChain agentSecurityFilterChain(HttpSecurity http, AgentPairingService service,
                                                CoreTenantContext tenants) throws Exception {
        return http.securityMatcher("/api/agent/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/agent/v1/enroll").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/agent/v1/upload", "/api/agent/v1/camera/snapshot").hasAuthority("AGENT_UPLOAD")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                        response.setStatus(401)))
                // Constructed here, not a servlet filter bean: it must run only in this chain.
                .addFilterBefore(new AgentCredentialFilter(service, tenants), AnonymousAuthenticationFilter.class)
                .build();
    }
}
