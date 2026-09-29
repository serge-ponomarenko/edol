package org.spon.edolnotify.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureMultiTenantUnavailableConfiguration {

    @Bean
    Object secureMultiTenantStageGate() {
        throw new IllegalStateException(
                "secure-multi-tenant Notify startup requires accepted Stage 7 service authentication"
        );
    }
}
