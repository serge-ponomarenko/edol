package org.spon.edolams.service;

import jakarta.annotation.PostConstruct;
import org.spon.edolams.config.AmsTerminalProperties;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Derives stored digests; plaintext pairing codes and terminal secrets are never persisted. */
@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalCredentialCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AmsTerminalProperties properties;

    public TerminalCredentialCodec(AmsTerminalProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        int currentVersion = keyVersion();
        pairingKey(currentVersion);
        validatePairingKeyVersions(currentVersion);
        credentialKey(currentVersion);
    }

    public String newPairingCode() {
        final char[] alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
        char[] code = new char[10];
        for (int index = 0; index < code.length; index++) {
            code[index] = alphabet[RANDOM.nextInt(alphabet.length)];
        }
        return new String(code);
    }

    public String newTerminalSecret() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public byte[] pairingCodeDigest(String pairingCode) {
        return pairingCodeDigest(keyVersion(), pairingCode);
    }

    byte[] pairingCodeDigest(int keyVersion, String pairingCode) {
        return hmac(pairingKey(keyVersion), normalizePairingCode(pairingCode));
    }

    public PairingCodeDigests pairingCodeDigests(String pairingCode) {
        int currentVersion = keyVersion();
        validatePairingKeyVersions(currentVersion);
        int previousVersion = currentVersion - 1;
        if (!properties.pairingCodeHmacKeys().containsKey(previousVersion)) {
            return new PairingCodeDigests(
                    pairingCodeDigest(currentVersion, pairingCode), currentVersion, null, null
            );
        }
        return new PairingCodeDigests(
                pairingCodeDigest(currentVersion, pairingCode), currentVersion,
                pairingCodeDigest(previousVersion, pairingCode), previousVersion
        );
    }

    public byte[] terminalCredentialDigest(int keyVersion, String secret) {
        return hmac(credentialKey(keyVersion), secret);
    }

    public boolean matchesTerminalCredential(byte[] expectedDigest, int keyVersion, String secret) {
        return MessageDigest.isEqual(expectedDigest, terminalCredentialDigest(keyVersion, secret));
    }

    public int keyVersion() {
        if (properties.keyVersion() < 1) {
            throw new IllegalStateException("AMS terminal credential key version must be positive");
        }
        return properties.keyVersion();
    }

    private byte[] hmac(String configuredKey, String value) {
        try {
            if (configuredKey == null || configuredKey.isBlank()) {
                throw new IllegalStateException("AMS terminal HMAC key is required in secure multi-tenant mode");
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(configuredKey), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AMS terminal HMAC key must be Base64 encoded", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive AMS terminal credential digest", exception);
        }
    }

    private String pairingKey(int keyVersion) {
        return key(properties.pairingCodeHmacKeys(), keyVersion, "pairing-code");
    }

    private void validatePairingKeyVersions(int currentVersion) {
        if (properties.pairingCodeHmacKeys() == null || properties.pairingCodeHmacKeys().isEmpty()) {
            throw new IllegalStateException("AMS terminal pairing-code HMAC keys are required in secure multi-tenant mode");
        }
        int previousVersion = currentVersion - 1;
        boolean hasUnsupportedVersion = properties.pairingCodeHmacKeys().keySet().stream()
                .anyMatch(version -> version != currentVersion && version != previousVersion);
        if (hasUnsupportedVersion) {
            throw new IllegalStateException(
                    "AMS terminal pairing-code HMAC keys may contain only the current and immediately previous versions"
            );
        }
    }

    private String credentialKey(int keyVersion) {
        return key(properties.credentialHmacKeys(), keyVersion, "credential");
    }

    private String key(java.util.Map<Integer, String> keys, int keyVersion, String purpose) {
        String key = keys == null ? null : keys.get(keyVersion);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("AMS terminal " + purpose + " HMAC key is missing for version " + keyVersion);
        }
        return key;
    }

    private String normalizePairingCode(String pairingCode) {
        if (pairingCode == null) {
            return "";
        }
        return pairingCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static final class PairingCodeDigests {

        private final byte[] currentDigest;
        private final int currentKeyVersion;
        private final byte[] previousDigest;
        private final Integer previousKeyVersion;

        PairingCodeDigests(
                byte[] currentDigest,
                int currentKeyVersion,
                byte[] previousDigest,
                Integer previousKeyVersion
        ) {
            this.currentDigest = currentDigest.clone();
            this.currentKeyVersion = currentKeyVersion;
            this.previousDigest = previousDigest == null ? null : previousDigest.clone();
            this.previousKeyVersion = previousKeyVersion;
        }

        public byte[] currentDigest() {
            return currentDigest.clone();
        }

        public int currentKeyVersion() {
            return currentKeyVersion;
        }

        public byte[] previousDigest() {
            return previousDigest == null ? null : previousDigest.clone();
        }

        public Integer previousKeyVersion() {
            return previousKeyVersion;
        }
    }
}
