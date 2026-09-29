package org.spon.edolhub.service;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record OidcIdentity(String issuer, String subject, String displayName, String email) {

    public static OidcIdentity from(OidcUser user) {
        String email = Boolean.TRUE.equals(user.getEmailVerified()) ? user.getEmail() : null;
        return new OidcIdentity(
                user.getIssuer().toString(),
                user.getSubject(),
                user.getFullName() == null ? user.getPreferredUsername() : user.getFullName(),
                email
        );
    }
}
