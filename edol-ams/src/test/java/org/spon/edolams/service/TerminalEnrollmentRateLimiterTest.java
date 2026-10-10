package org.spon.edolams.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalEnrollmentRateLimiterTest {

    @Test
    void blocksTheSixthFailureInTheFiveMinuteSourceWindow() {
        TerminalEnrollmentRateLimiter limiter = new TerminalEnrollmentRateLimiter(
                Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneOffset.UTC)
        );

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.failed("192.0.2.12");
        }

        assertThat(limiter.isBlocked("192.0.2.12")).isTrue();
        limiter.succeeded("192.0.2.12");
        assertThat(limiter.isBlocked("192.0.2.12")).isFalse();
    }
}
