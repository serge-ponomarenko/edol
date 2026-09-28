package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.MissingTenantContextException;
import org.spon.edolhub.service.TenantContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubTenantIdentifierResolverTest {

    @Test
    void failsClosedWithoutTenantContextAndResolvesOnlyAnActiveScope() {
        TenantContext context = new TenantContext();
        HubTenantIdentifierResolver resolver = new HubTenantIdentifierResolver(context);
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(MissingTenantContextException.class);

        try (TenantContext.TenantScope ignored = context.open(tenantId)) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(tenantId);
        }

        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(MissingTenantContextException.class);
    }
}
