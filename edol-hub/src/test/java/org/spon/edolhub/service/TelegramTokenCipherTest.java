package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramTokenCipherTest {
    private final TelegramTokenCipher cipher = new TelegramTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsWithRandomNonceAndTenantAuthenticatedData() {
        UUID tenant = UUID.randomUUID();
        String first = cipher.encrypt(tenant, "test-only-token");
        String second = cipher.encrypt(tenant, "test-only-token");
        assertThat(first).isNotEqualTo(second).doesNotContain("test-only-token");
        assertThat(cipher.decrypt(tenant, first)).isEqualTo("test-only-token");
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), first))
                .hasMessage("Telegram secret storage is unavailable").hasNoCause();
    }

    @Test
    void rejectsTamperingAndMissingKeyWithoutExposingInput() {
        UUID tenant = UUID.randomUUID();
        String ciphertext = cipher.encrypt(tenant, "test-only-token");
        assertThatThrownBy(() -> cipher.decrypt(tenant, ciphertext.substring(0, ciphertext.length() - 4) + "AAAA"))
                .hasMessage("Telegram secret storage is unavailable");
        TelegramTokenCipher absent = new TelegramTokenCipher("");
        assertThat(absent.available()).isFalse();
        assertThatThrownBy(() -> absent.encrypt(tenant, "test-only-token"))
                .hasMessage("Telegram secret storage is unavailable");
        assertThatThrownBy(() -> new TelegramTokenCipher("invalid-key"))
                .hasMessage("Telegram encryption requires a base64-encoded 32-byte key").hasNoCause();
    }
}
