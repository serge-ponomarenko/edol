package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDefaultTenantCompatibilityWebConfigurationTest {

    @Test
    void enumeratesOnlyTheTemporaryPreAuthHttpIngressPaths() {
        assertThat(LegacyDefaultTenantCompatibilityWebConfiguration.LEGACY_HTTP_PATHS).containsExactly(
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
    }
}
