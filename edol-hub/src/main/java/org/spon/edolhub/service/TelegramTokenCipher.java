package org.spon.edolhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class TelegramTokenCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public TelegramTokenCipher(@Value("${edol-hub.telegram.encryption-key:}") String encodedKey) {
        if (encodedKey.isBlank()) {
            key = null;
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) {
                throw new IllegalArgumentException();
            }
            key = new SecretKeySpec(bytes, "AES");
            Arrays.fill(bytes, (byte) 0);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Telegram encryption requires a base64-encoded 32-byte key");
        }
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(UUID tenantId, String token) {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, tenantId, nonce).doFinal(token.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw unavailable();
        }
    }

    public String decrypt(UUID tenantId, String value) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            if (nonce.length != 12) {
                throw new IllegalArgumentException();
            }
            return new String(cipher(Cipher.DECRYPT_MODE, tenantId, nonce)
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    public String fingerprint(String token) {
        try {
            // Telegram tokens have high entropy; this digest only prevents sharing one bot across tenants.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw unavailable();
        }
    }

    private Cipher cipher(int mode, UUID tenantId, byte[] nonce) throws GeneralSecurityException {
        if (key == null) {
            throw unavailable();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(tenantId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("Telegram secret storage is unavailable");
    }
}
