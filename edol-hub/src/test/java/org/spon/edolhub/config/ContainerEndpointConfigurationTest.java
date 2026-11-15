package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerEndpointConfigurationTest {

    @Test
    void usesContainerEndpointsBeforeOperatorLocalOverrides() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://postgres:5432/${POSTGRES_DB}")
                .containsEntry("edol-core.url", "http://edolcore:8080")
                .containsEntry("edol-ams.url", "http://edolams:8099")
                .containsEntry("mqttServer.url", "tcp://mqtt:1883");
    }
}
