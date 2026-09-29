package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentModeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DeploymentModeConfiguration.class);

    @Test
    void failsWhenDeploymentModeIsMissing() {
        contextRunner.run(context ->
                assertThat(context.getStartupFailure())
                        .hasMessageContaining("edol.deployment.mode is required")
        );
    }

    @Test
    void bindsHomeDeploymentMode() {
        contextRunner.withPropertyValues("edol.deployment.mode=home")
                .run(context ->
                        assertThat(context.getBean(DeploymentMode.class)).isEqualTo(DeploymentMode.HOME)
                );
    }
}
