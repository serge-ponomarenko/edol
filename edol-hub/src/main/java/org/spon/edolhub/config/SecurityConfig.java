package org.spon.edolhub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private final HubSessionExpiryFilter hubSessionExpiryFilter;
    private final ActiveTenantContextFilter activeTenantContextFilter;
    private final ObjectProvider<AmsCompatibilityIngressFilter> amsCompatibilityIngressFilter;

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
    SecurityFilterChain homeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
    SecurityFilterChain secureSecurityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            HttpSessionOAuth2AuthorizedClientRepository authorizedClientRepository,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver
    ) throws Exception {
        OidcClientInitiatedLogoutSuccessHandler logoutHandler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");
        AmsCompatibilityIngressFilter amsIngress = amsCompatibilityIngressFilter.getObject();

        http.csrf(csrf -> csrf.ignoringRequestMatchers(amsIngress))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/favicon.svg", "/favicon_*.svg", "/img/**", "/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/tenants/select").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .authorizedClientRepository(authorizedClientRepository)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .successHandler(oidcLoginSuccessHandler)
                )
                .logout(logout -> logout.logoutSuccessHandler(logoutHandler))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .addFilterAfter(hubSessionExpiryFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(amsIngress, SecurityContextHolderFilter.class)
                .addFilterAfter(activeTenantContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
    HttpSessionOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}


