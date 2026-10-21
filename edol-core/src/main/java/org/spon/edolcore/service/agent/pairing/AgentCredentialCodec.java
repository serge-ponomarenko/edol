package org.spon.edolcore.service.agent.pairing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/** Agent-specific domain-separated HMACs; no plaintext code or credential is stored. */
public class AgentCredentialCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private final AgentPairingProperties properties;

    public AgentCredentialCodec(AgentPairingProperties properties) {
        this.properties = properties;
        validateKeys(properties.pairingKeys());
        validateKeys(properties.credentialKeys());
    }

    private void validateKeys(Map<Integer, String> keys) {
        int current = version();
        if (current < 1 || keys == null || !keys.containsKey(current)
                || keys.keySet().stream().anyMatch(v -> v != current && v != current - 1)) {
            throw new IllegalStateException("Agent keys require current and optional immediately previous versions");
        }
        for (String key : keys.values()) {
            if (key == null || Base64.getDecoder().decode(key).length < 32) {
                throw new IllegalStateException("Agent HMAC keys require at least 32 random bytes encoded as Base64");
            }
        }
    }

    public int version() {
        return properties.keyVersion();
    }

    public String newCode() {
        StringBuilder code = new StringBuilder(12);
        for (int index = 0; index < 12; index++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public boolean validCode(String code) {
        return code != null && code.length() == 12
                && code.toUpperCase(Locale.ROOT).chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    public String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public byte[] codeDigest(int version, String code) {
        return digest(properties.pairingKeys(), version, "edol-agent-pairing:", code.toUpperCase(Locale.ROOT));
    }

    public Integer previousPairingVersion() {
        return properties.pairingKeys().containsKey(version() - 1) ? version() - 1 : null;
    }

    public byte[] credentialDigest(int version, String secret) {
        return digest(properties.credentialKeys(), version, "edol-agent-credential:", secret);
    }

    public boolean matches(byte[] expected, int version, String secret) {
        return properties.credentialKeys().containsKey(version)
                && MessageDigest.isEqual(expected, credentialDigest(version, secret));
    }

    private byte[] digest(Map<Integer, String> keys, int version, String domain, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(keys.get(version)), "HmacSHA256"));
            return mac.doFinal((domain + value).getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive agent digest", exception);
        }
    }
}
