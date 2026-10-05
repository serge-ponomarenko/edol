package org.spon.edolcore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Value("${bot.webAdminName}")
    private String adminName;

    @Value("${bot.webAdminPassword}")
    private String adminPassword;

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
    public SecurityFilterChain homeSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // disable for simplicity
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(
                                        "/actuator/health"
                                ).permitAll()
                                .anyRequest()
                                .permitAll()
                        //.authenticated()
                )
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
    public SecurityFilterChain secureSecurityFilterChain(
            HttpSecurity http,
            CoreTenantContextFilter coreTenantContextFilter,
            ObjectProvider<CoreLegacyCompatibilityFilter> legacyCompatibilityFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/printers").hasAuthority("SCOPE_core.printer.manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/printers/**").hasAuthority("SCOPE_core.printer.manage")
                        .requestMatchers(HttpMethod.DELETE, "/api/printers/**").hasAuthority("SCOPE_core.printer.manage")
                        .requestMatchers(HttpMethod.PATCH, "/api/printers/*/connection").hasAuthority("SCOPE_core.printer.manage")
                        .requestMatchers(HttpMethod.GET, "/api/printers/*/connection").hasAuthority("SCOPE_core.printer.read")
                        .requestMatchers("/api/printers/*/state", "/api/printers/state").hasAnyAuthority("SCOPE_core.state.read", CoreLegacyCompatibilityFilter.LEGACY_AUTHORITY)
                        .requestMatchers("/api/printers/*/commands/**").hasAnyAuthority("SCOPE_core.command.execute", CoreLegacyCompatibilityFilter.LEGACY_AUTHORITY)
                        .requestMatchers("/api/printers/*/media/**", "/api/printers/*/camera/**").hasAnyAuthority("SCOPE_core.media.read", CoreLegacyCompatibilityFilter.LEGACY_AUTHORITY)
                        .requestMatchers("/api/printers", "/api/printers/*").hasAnyAuthority("SCOPE_core.printer.read", CoreLegacyCompatibilityFilter.LEGACY_AUTHORITY)
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> { }))
                .addFilterAfter(coreTenantContextFilter, BearerTokenAuthenticationFilter.class);
        CoreLegacyCompatibilityFilter legacyFilter = legacyCompatibilityFilter.getIfAvailable();
        if (legacyFilter != null) {
            http.addFilterBefore(legacyFilter, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails admin = User.withUsername(adminName)
                .password(adminPassword)
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    @ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}


