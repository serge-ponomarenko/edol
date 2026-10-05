package org.spon.edolcore.service.tenant;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreTenantContextTest {

    private static final UUID FIRST_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void isFailClosedAndRestoresNestedScope() {
        CoreTenantContext context = new CoreTenantContext();

        assertThatThrownBy(context::getCurrentTenantId)
                .isInstanceOf(MissingCoreTenantContextException.class);

        try (CoreTenantContext.TenantScope outer = context.open(FIRST_TENANT);
             CoreTenantContext.TenantScope nested = context.open(FIRST_TENANT)) {
            assertThatThrownBy(() -> context.open(SECOND_TENANT))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThatThrownBy(context::getCurrentTenantId)
                .isInstanceOf(MissingCoreTenantContextException.class);
    }
}
