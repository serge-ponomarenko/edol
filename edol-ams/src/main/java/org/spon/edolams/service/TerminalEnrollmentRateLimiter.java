package org.spon.edolams.service;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A bounded per-source failure window. Production ingress must also rate limit this endpoint. */
@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalEnrollmentRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private final Map<String, FailureWindow> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    TerminalEnrollmentRateLimiter() {
        this(Clock.systemUTC());
    }

    TerminalEnrollmentRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String source) {
        FailureWindow window = failures.get(source);
        return window != null && !window.isExpired(clock.instant()) && window.count() >= MAX_FAILURES;
    }

    public void failed(String source) {
        Instant now = clock.instant();
        failures.compute(source, (ignored, window) -> window == null || window.isExpired(now)
                ? new FailureWindow(now.plus(WINDOW), 1)
                : new FailureWindow(window.expiresAt(), window.count() + 1));
    }

    public void succeeded(String source) {
        failures.remove(source);
    }

    private record FailureWindow(Instant expiresAt, int count) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
