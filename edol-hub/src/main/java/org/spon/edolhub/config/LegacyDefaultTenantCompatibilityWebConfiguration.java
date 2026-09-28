package org.spon.edolhub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class LegacyDefaultTenantCompatibilityWebConfiguration implements WebMvcConfigurer {

    static final List<String> LEGACY_HTTP_PATHS = List.of(
            "/",
            "/printers/**",
            "/vendors/**",
            "/materials/**",
            "/filaments/**",
            "/filament-spools/**",
            "/s/**",
            "/api/printers/**",
            "/api/spools/**",
            "/api/colors/**",
            "/api/print/**",
            "/dev/printers/**"
    );

    private final LegacyDefaultTenantCompatibilityInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns(LEGACY_HTTP_PATHS);
    }
}
