package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPairingModeTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AgentPairingConfiguration.class);

    @Test
    void homeNeverInitializesPairingEvenWhenTheFeatureFlagIsSet() {
        runner.withPropertyValues("edol.deployment.mode=home", "edol-core.agent.enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AgentCredentialCodec.class).doesNotHaveBean(AgentPairingService.class));
    }

    @Test
    void secureModeDefaultsToDisabledWithoutCredentialKeys() {
        runner.withPropertyValues("edol.deployment.mode=secure-multi-tenant")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AgentCredentialCodec.class).doesNotHaveBean(AgentPairingService.class));
    }
}
