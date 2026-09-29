package org.spon.edolhub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureMultiTenantUnavailableConfiguration {

    @Bean
    Object secureMultiTenantStageGate() {
        throw new IllegalStateException(
                "secure-multi-tenant Hub startup requires accepted Stage 4 BFF authentication"
        );
    }
}
