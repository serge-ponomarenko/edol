package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DevSmokeConfigurationTest {

    @Test
    void requiresAnExplicitDisposableDatasourceAndDisablesCoreAndMqttEndpoints() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev-smoke.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${EDOL_HUB_SMOKE_DB_JDBC_URL}")
                .containsEntry("spring.flyway.clean-disabled", true)
                .containsEntry("edol-core.url", "http://127.0.0.1:1")
                .containsEntry("mqttServer.url", "tcp://127.0.0.1:1")
                .containsEntry("edol-hub.registration.self-service-enabled", true)
                .containsEntry("edol-hub.session.maximum-age", "PT60S");
    }
}
