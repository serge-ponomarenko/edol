package org.spon.edolams.config;

import org.spon.edol.deployment.DeploymentMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("edol.deployment")
public record DeploymentModeProperties(DeploymentMode mode) {
}
