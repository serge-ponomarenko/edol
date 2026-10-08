package org.spon.edolcore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
class Jackson2ObjectMapperConfigurationTest {

    @Test
    void configuresTheJackson2ObjectMapperRequiredByCoreIntegrationEvents() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .run(context -> assertThat(context).hasSingleBean(ObjectMapper.class));
    }
}
