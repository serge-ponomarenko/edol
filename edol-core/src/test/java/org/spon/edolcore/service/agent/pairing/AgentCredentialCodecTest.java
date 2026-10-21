package org.spon.edolcore.service.agent.pairing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentCredentialCodecTest {
    static AgentPairingProperties properties() {
        return new AgentPairingProperties(1, Map.of(1, key(1)), Map.of(1, key(2)));
    }

    private static String key(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void generatesBoundedCodesAndSecretsAndSeparatesTheirDigests() {
        var codec = new AgentCredentialCodec(properties());
        assertThat(codec.newCode()).matches("[0-9A-HJKMNP-TV-Z]{12}");
        assertThat(codec.newSecret()).matches("[A-Za-z0-9_-]{43}");
        assertThat(codec.validCode("abcdefghijkl")).isFalse();
        assertThat(codec.validCode("0123456789ab")).isTrue();
        assertThat(codec.codeDigest(1, "0123456789ab")).isEqualTo(codec.codeDigest(1, "0123456789AB"));
        byte[] digest = codec.credentialDigest(1, "synthetic");
        assertThat(codec.matches(digest, 1, "synthetic")).isTrue();
        assertThat(codec.matches(digest, 1, "different")).isFalse();
        assertThat(codec.matches(digest, 2, "synthetic")).isFalse();
        assertThat(properties().toString()).doesNotContain(key(1), key(2));
        assertThat(new AgentPairingService.Enrollment(java.util.UUID.randomUUID(), "synthetic").toString())
                .doesNotContain("synthetic");
    }

    @Test
    void requiresStrongKeysAndOnlyCurrentPreviousOverlap() {
        assertThatThrownBy(() -> new AgentCredentialCodec(new AgentPairingProperties(1, Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AgentCredentialCodec(new AgentPairingProperties(1,
                Map.of(1, "YQ=="), Map.of(1, key(2))))).isInstanceOf(IllegalStateException.class);
        var codec = new AgentCredentialCodec(new AgentPairingProperties(2,
                Map.of(1, key(1), 2, key(3)), Map.of(1, key(2), 2, key(4))));
        assertThat(codec.previousPairingVersion()).isEqualTo(1);
        assertThat(codec.matches(new AgentCredentialCodec(properties()).credentialDigest(1, "test"), 1, "test")).isTrue();
        assertThatThrownBy(() -> new AgentCredentialCodec(new AgentPairingProperties(3,
                Map.of(1, key(1), 3, key(3)), Map.of(3, key(4))))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void boundsConcurrentSourceBudgetsAndExpiresTheWindow() {
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        var time = new java.util.concurrent.atomic.AtomicReference<>(now);
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return time.get(); }
        };
        var limiter = new AgentEnrollmentLimiter(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(limiter.acquire("source")).isTrue();
        }
        assertThat(limiter.acquire("source")).isFalse();
        for (int source = 1; source < 4096; source++) {
            assertThat(limiter.acquire("source-" + source)).isTrue();
        }
        assertThat(limiter.acquire("overflow")).isFalse();
        time.set(now.plusSeconds(300));
        assertThat(limiter.acquire("source")).isTrue();
        assertThat(limiter.acquire("overflow")).isTrue();
    }
}
