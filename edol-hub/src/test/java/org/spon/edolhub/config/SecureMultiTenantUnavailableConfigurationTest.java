package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecureMultiTenantUnavailableConfigurationTest {

    @Test
    void failsBeforeSecureBffAuthenticationExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        DeploymentModeConfiguration.class,
                        SecureMultiTenantUnavailableConfiguration.class
                )
                .withPropertyValues("edol.deployment.mode=secure-multi-tenant")
                .run(context ->
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("accepted Stage 4 BFF authentication")
                );
    }
}
