package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.messaging.MessageChannel;

import static org.assertj.core.api.Assertions.assertThat;

class MqttConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MqttConfig.class);

    @Test
    void secureMultiTenantModeDoesNotCreateMqttInfrastructure() {
        contextRunner
                .withPropertyValues("edol.deployment.mode=secure-multi-tenant")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MqttPahoClientFactory.class);
                    assertThat(context).doesNotHaveBean(MessageChannel.class);
                    assertThat(context).doesNotHaveBean(MessageProducer.class);
                });
    }
}
