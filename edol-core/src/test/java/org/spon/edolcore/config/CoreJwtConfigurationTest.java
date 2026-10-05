package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoreJwtConfigurationTest {

    @Test
    void rejectsATokenWithoutTheRequiredAudience() {
        Jwt token = jwt(List.of("other-api"));

        assertThat(CoreJwtConfiguration.audienceValidator("edol-core-api").validate(token).hasErrors()).isTrue();
    }

    @Test
    void acceptsATokenWithTheRequiredAudience() {
        Jwt token = jwt(List.of("other-api", "edol-core-api"));

        assertThat(CoreJwtConfiguration.audienceValidator("edol-core-api").validate(token).hasErrors()).isFalse();
    }

    private Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("aud", audience)
        );
    }
}
