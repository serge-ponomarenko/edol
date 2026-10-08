package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class Stage6SmokeConfigurationTest {

    @Test
    void requiresDedicatedDisposableResourcesAndEnablesOnlySecureHubMqtt() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(
                new ClassPathResource("application.yaml"),
                new ClassPathResource("application-secure-multi-tenant.yaml"),
                new ClassPathResource("application-stage6-smoke.yaml")
        );
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("server.address", "127.0.0.1")
                .containsEntry("spring.datasource.url", "${EDOL_HUB_STAGE6_SMOKE_DB_JDBC_URL}")
                .containsEntry("spring.datasource.username", "${EDOL_HUB_STAGE6_SMOKE_RUNTIME_DB_USER}")
                .containsEntry("spring.flyway.user", "${EDOL_HUB_STAGE6_SMOKE_FLYWAY_DB_USER}")
                .containsEntry("spring.flyway.clean-disabled", true)
                .containsEntry("edol-core.url", "${EDOL_CORE_STAGE6_SMOKE_URL}")
                .containsEntry("mqttServer.url", "${EDOL_STAGE6_SMOKE_MQTT_URL}")
                .containsEntry("edol-hub.runtime.mqtt-enabled", true)
                .containsEntry("edol-hub.registration.self-service-enabled", true)
                .containsEntry("edol-hub.session.maximum-age", "PT60S");
    }
}
