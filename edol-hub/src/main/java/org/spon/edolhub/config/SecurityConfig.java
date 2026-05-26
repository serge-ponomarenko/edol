package org.spon.edolhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers ->
                        headers.frameOptions(
                                HeadersConfigurer.FrameOptionsConfig::sameOrigin
                        )
                )
                .authorizeHttpRequests(
                        auth ->
                                auth.anyRequest().permitAll()
                );

        return http.build();
    }


}


