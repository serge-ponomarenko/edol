package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private static final UUID FIRST_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID SECOND_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000502");

    @Test
    void failsClosedWithoutAnActiveScope() {
        TenantContext context = new TenantContext();

        assertThatThrownBy(context::getCurrentTenantId)
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void cleansUpNestedScopeAndRejectsTenantReplacement() {
        TenantContext context = new TenantContext();

        try (TenantContext.TenantScope outer = context.open(FIRST_TENANT);
             TenantContext.TenantScope nested = context.open(FIRST_TENANT)) {
            assertThat(context.getCurrentTenantId()).isEqualTo(FIRST_TENANT);
            assertThatThrownBy(() -> context.open(SECOND_TENANT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot replace");
        }

        assertThatThrownBy(context::getCurrentTenantId)
                .isInstanceOf(MissingTenantContextException.class);
    }
}
