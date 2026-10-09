package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HubJwtConfigurationTest {

    @Test
    void rejectsTokenWithoutRequiredAudience() {
        assertThat(HubJwtConfiguration.audienceValidator("edol-hub-api")
                .validate(jwt(List.of("other-api"))).hasErrors()).isTrue();
    }

    @Test
    void acceptsTokenWithRequiredAudience() {
        assertThat(HubJwtConfiguration.audienceValidator("edol-hub-api")
                .validate(jwt(List.of("other-api", "edol-hub-api"))).hasErrors()).isFalse();
    }

    private Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("aud", audience));
    }
}
