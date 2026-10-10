package org.spon.edolams.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectProvider<AmsHubServiceTenantContextFilter> hubServiceTenantContextFilter;

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
    SecurityFilterChain homeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
    SecurityFilterChain secureSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/terminal/v1/**").permitAll()
                        .requestMatchers("/internal/terminals/**").hasAuthority("SCOPE_ams.terminal.manage")
                        .requestMatchers("/ams/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/error").permitAll()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> { }))
                .addFilterAfter(hubServiceTenantContextFilter.getObject(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}


