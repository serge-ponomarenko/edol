package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class Stage6SmokeConfigurationTest {

    @Test
    void enablesOnlyTheDedicatedIntegrationPublisherForDisposableMqtt() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(
                new ClassPathResource("application.yaml"),
                new ClassPathResource("application-secure-multi-tenant.yaml"),
                new ClassPathResource("application-stage6-smoke.yaml")
        );
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("server.address", "127.0.0.1")
                .containsEntry("spring.datasource.url", "${EDOL_CORE_STAGE6_SMOKE_DB_JDBC_URL}")
                .containsEntry("spring.datasource.username", "${EDOL_CORE_STAGE6_SMOKE_RUNTIME_DB_USER}")
                .containsEntry("spring.flyway.user", "${EDOL_CORE_STAGE6_SMOKE_FLYWAY_DB_USER}")
                .containsEntry("edol-core.catalog-datasource.username", "${EDOL_CORE_STAGE6_SMOKE_CATALOG_DB_USER}")
                .containsEntry("spring.flyway.clean-disabled", true)
                .containsEntry("mqttServer.url", "${EDOL_STAGE6_SMOKE_MQTT_URL}")
                .containsEntry("edol-core.runtime.mqtt-enabled", false)
                .containsEntry("edol-core.runtime.integration-mqtt-enabled", true)
                .containsEntry("edol-core.runtime.printer-runtime-enabled", false)
                .containsEntry("camera.store-snapshots", false);
    }
}
