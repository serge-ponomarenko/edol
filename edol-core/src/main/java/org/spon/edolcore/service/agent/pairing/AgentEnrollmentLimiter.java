package org.spon.edolcore.service.agent.pairing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Bounded process-local attempt budget; deployment ingress must also rate limit. */
public class AgentEnrollmentLimiter {
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public AgentEnrollmentLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean acquire(String source) {
        Instant now = clock.instant();
        windows.values().removeIf(window -> !window.until().isAfter(now));
        Window window = windows.get(source);
        if (window == null && windows.size() >= 4096 || window != null && window.attempts() >= 5) {
            return false;
        }
        windows.put(source, new Window(window == null ? now.plus(Duration.ofMinutes(5)) : window.until(),
                window == null ? 1 : window.attempts() + 1));
        return true;
    }

    private record Window(Instant until, int attempts) { }
}
