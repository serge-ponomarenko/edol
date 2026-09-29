package org.spon.edolcore.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureMultiTenantUnavailableConfiguration {

    @Bean
    Object secureMultiTenantStageGate() {
        throw new IllegalStateException(
                "secure-multi-tenant Core startup requires accepted Stage 5 service authentication"
        );
    }
}
