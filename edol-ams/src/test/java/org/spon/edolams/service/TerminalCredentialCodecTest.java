package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.spon.edolams.config.AmsTerminalProperties;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(rotatingCodec.pairingCodeDigests("ABCDEFGHJK"))
                .contains(rotatingCodec.pairingCodeDigest(2, "ABCDEFGHJK"))
                .contains(rotatingCodec.pairingCodeDigest(1, "ABCDEFGHJK"));
    }
}
