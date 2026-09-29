package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityContextTest {

    @Test
    void scopesValidatedIdentityAndRemovesItOnClose() {
        IdentityContext context = new IdentityContext();

        try (IdentityContext.IdentityScope ignored = context.open("https://issuer.example/realm", "subject")) {
            assertThat(context.getCurrentIdentity())
                    .isEqualTo(new IdentityContext.Identity("https://issuer.example/realm", "subject"));
            assertThat(context.hasCurrentIdentity()).isTrue();
        }

        assertThat(context.hasCurrentIdentity()).isFalse();
        assertThatThrownBy(context::getCurrentIdentity).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsReplacingAnActiveIdentity() {
        IdentityContext context = new IdentityContext();

        try (IdentityContext.IdentityScope ignored = context.open("issuer", "first")) {
            assertThatThrownBy(() -> context.open("issuer", "second"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
