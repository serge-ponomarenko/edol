package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.MissingTenantContextException;
import org.spon.edolhub.service.TenantContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubTenantIdentifierResolverTest {

    @Test
    void failsClosedWithoutTrustedContextAndResolvesOnlyAnActiveTenantScope() {
        TenantContext tenantContext = new TenantContext();
        IdentityContext identityContext = new IdentityContext();
        HubTenantIdentifierResolver resolver = new HubTenantIdentifierResolver(tenantContext, identityContext);
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(MissingTenantContextException.class);

        try (TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(tenantId);
        }

        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void resolvesPreTenantIdentifierOnlyWithinAnOidcIdentityScope() {
        TenantContext tenantContext = new TenantContext();
        IdentityContext identityContext = new IdentityContext();
        HubTenantIdentifierResolver resolver = new HubTenantIdentifierResolver(tenantContext, identityContext);

        try (IdentityContext.IdentityScope ignored = identityContext.open("https://issuer.example/realms/edol", "subject")) {
            assertThat(resolver.resolveCurrentTenantIdentifier())
                    .isEqualTo(HubTenantIdentifierResolver.PRE_TENANT_IDENTIFIER);
        }

        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier)
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void prefersAnEstablishedTenantOverThePreTenantIdentifier() {
        TenantContext tenantContext = new TenantContext();
        IdentityContext identityContext = new IdentityContext();
        HubTenantIdentifierResolver resolver = new HubTenantIdentifierResolver(tenantContext, identityContext);
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000702");

        try (IdentityContext.IdentityScope identityScope = identityContext.open("https://issuer.example/realms/edol", "subject");
             TenantContext.TenantScope tenantScope = tenantContext.open(tenantId)) {
            assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(tenantId);
        }
    }
}
