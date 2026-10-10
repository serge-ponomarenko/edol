package org.spon.edolams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("edol-ams.terminal")
public record AmsTerminalProperties(
        java.util.Map<Integer, String> pairingCodeHmacKeys,
        java.util.Map<Integer, String> credentialHmacKeys,
        int keyVersion
) {
}
