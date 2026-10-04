package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DevDataSmokeConfigurationTest {

    @Test
    void requiresDedicatedCloneDatasourceAndDisablesSelfServiceJit() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev-data-smoke.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${EDOL_HUB_DATA_SMOKE_DB_JDBC_URL}")
                .containsEntry("spring.flyway.clean-disabled", true)
                .containsEntry("edol-core.url", "http://127.0.0.1:1")
                .containsEntry("mqttServer.url", "tcp://127.0.0.1:1")
                .containsEntry("edol-hub.registration.self-service-enabled", false)
                .containsEntry("edol-hub.session.maximum-age", "PT60S");
    }

    @Test
    void overridesBaseContainerEndpointsForTheSecureHubOnlyProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(
                new ClassPathResource("application.yaml"),
                new ClassPathResource("application-secure-multi-tenant.yaml"),
                new ClassPathResource("application-dev-data-smoke.yaml")
        );
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${EDOL_HUB_DATA_SMOKE_DB_JDBC_URL}")
                .containsEntry("edol-core.url", "http://127.0.0.1:1")
                .containsEntry("mqttServer.url", "tcp://127.0.0.1:1")
                .containsEntry("edol-hub.registration.self-service-enabled", false);
    }
}
