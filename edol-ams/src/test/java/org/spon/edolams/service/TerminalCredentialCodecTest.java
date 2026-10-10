package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.spon.edolams.config.AmsTerminalProperties;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalCredentialCodecTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final TerminalCredentialCodec codec = new TerminalCredentialCodec(
            new AmsTerminalProperties(Map.of(1, KEY), Map.of(1, KEY), 1)
    );

    @Test
    void generatesCrockfordPairingCodeWithoutAmbiguousCharacters() {
        String code = codec.newPairingCode();

        assertThat(code).matches("[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{10}");
    }

    @Test
    void storesOnlyReproducibleHmacDigestsAndComparesTerminalSecrets() {
        String secret = codec.newTerminalSecret();
        byte[] digest = codec.terminalCredentialDigest(1, secret);

        assertThat(codec.matchesTerminalCredential(digest, 1, secret)).isTrue();
        assertThat(codec.matchesTerminalCredential(digest, 1, codec.newTerminalSecret())).isFalse();
        assertThat(codec.pairingCodeDigest("abcde-fghi")).isEqualTo(codec.pairingCodeDigest("ABCDE-FGHI"));
    }

    @Test
    void acceptsAStillConfiguredPreviousPairingKeyDuringShortRotationOverlap() {
        String previousKey = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String currentKey = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        TerminalCredentialCodec rotatingCodec = new TerminalCredentialCodec(
                new AmsTerminalProperties(Map.of(1, previousKey, 2, currentKey), Map.of(2, currentKey), 2)
        );

        TerminalCredentialCodec.PairingCodeDigests digests = rotatingCodec.pairingCodeDigests("ABCDEFGHJK");

        assertThat(digests.currentKeyVersion()).isEqualTo(2);
        assertThat(digests.currentDigest()).isEqualTo(rotatingCodec.pairingCodeDigest(2, "ABCDEFGHJK"));
        assertThat(digests.previousKeyVersion()).isEqualTo(1);
        assertThat(digests.previousDigest()).isEqualTo(rotatingCodec.pairingCodeDigest(1, "ABCDEFGHJK"));
    }

    @Test
    void rejectsStalePairingKeyVersionsOutsideTheSinglePreviousKeyOverlap() {
        String firstKey = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String secondKey = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        String currentKey = Base64.getEncoder().encodeToString(new byte[]{7, 8, 9});
        TerminalCredentialCodec rotatingCodec = new TerminalCredentialCodec(
                new AmsTerminalProperties(Map.of(1, firstKey, 2, secondKey, 3, currentKey), Map.of(3, currentKey), 3)
        );

        assertThatThrownBy(rotatingCodec::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current and immediately previous");
    }
}
