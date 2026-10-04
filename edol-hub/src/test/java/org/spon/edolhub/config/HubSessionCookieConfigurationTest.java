package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HubSessionCookieConfigurationTest {

    @Test
    void declaresSecureHttpOnlyLaxCookieThirtyMinuteIdleTimeoutAndEightHourMaximumAge() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("server.servlet.session.timeout", "30m")
                .containsEntry("edol-hub.session.maximum-age", "8h")
                .containsEntry("server.servlet.session.cookie.name", "EDOL_SESSION")
                .containsEntry("server.servlet.session.cookie.secure", true)
                .containsEntry("server.servlet.session.cookie.http-only", true)
                .containsEntry("server.servlet.session.cookie.same-site", "lax");
    }
}
