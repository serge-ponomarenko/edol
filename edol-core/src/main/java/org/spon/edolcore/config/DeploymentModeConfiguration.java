package org.spon.edolcore.config;

import org.spon.edol.deployment.DeploymentMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeploymentModeProperties.class)
public class DeploymentModeConfiguration {

    @Bean
    DeploymentMode deploymentMode(DeploymentModeProperties properties) {
        if (properties.mode() == null) {
            throw new IllegalStateException("edol.deployment.mode is required");
        }
        return properties.mode();
    }
}
