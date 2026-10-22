package org.spon.edolcoreapi.security;

import java.net.URI;

/** Common fail-closed transport prerequisite; TLS trust uses the JVM trust store. */
public final class SecureMqttEndpoint {
    private SecureMqttEndpoint() { }

    public static void validate(boolean secure, String url, String username, String password) {
        if (!secure) return;
        URI uri = URI.create(url);
        if (!"ssl".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Secure MQTT requires a TLS endpoint and independent credentials");
        }
    }
}
