package org.spon.edolhub.service;

import org.springframework.stereotype.Service;

/**
 * Holds the validated OIDC identity while Hub performs pre-tenant work.
 */
@Service
public class IdentityContext {

    private final ThreadLocal<Identity> identity = new ThreadLocal<>();

    public boolean hasCurrentIdentity() {
        return identity.get() != null;
    }

    public Identity getCurrentIdentity() {
        Identity current = identity.get();
        if (current == null) {
            throw new IllegalStateException("OIDC identity context is required");
        }
        return current;
    }

    public IdentityScope open(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("OIDC issuer and subject are required");
        }
        Identity current = identity.get();
        Identity candidate = new Identity(issuer, subject);
        if (current != null && !current.equals(candidate)) {
            throw new IllegalStateException("Cannot replace an active OIDC identity context");
        }
        identity.set(candidate);
        return new IdentityScope(this, candidate, current == null);
    }

    private void close(Identity expected, boolean owner) {
        if (!owner) {
            return;
        }
        if (!expected.equals(identity.get())) {
            throw new IllegalStateException("OIDC identity context scope was closed out of order");
        }
        identity.remove();
    }

    public record Identity(String issuer, String subject) {
    }

    public static final class IdentityScope implements AutoCloseable {
        private final IdentityContext context;
        private final Identity identity;
        private final boolean owner;
        private boolean closed;

        private IdentityScope(IdentityContext context, Identity identity, boolean owner) {
            this.context = context;
            this.identity = identity;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (!closed) {
                context.close(identity, owner);
                closed = true;
            }
        }
    }
}
