package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DevProfileConfigurationTest {

    @Test
    void overridesBaseContainerEndpointsWithDedicatedLocalDevelopmentEndpoints() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(
                new ClassPathResource("application.yaml"),
                new ClassPathResource("application-dev.yaml")
        );
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://localhost:5433/${POSTGRES_DB}")
                .containsEntry("edol-core.url", "http://localhost:8080")
                .containsEntry("mqttServer.url", "tcp://192.168.0.200:1783");
    }
}
