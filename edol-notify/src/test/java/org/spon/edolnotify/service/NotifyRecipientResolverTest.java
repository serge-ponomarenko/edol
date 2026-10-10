package org.spon.edolnotify.service;

import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolnotify.config.NotifyRecipientProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyRecipientResolverTest {

    @Test
    void bindsAConfiguredChatToItsTenant() {
        UUID tenantId = UUID.randomUUID();
        NotifyTenantContext context = new NotifyTenantContext();
        NotifyRecipientResolver resolver = resolver(context, tenantId, 101L);

        try (NotifyTenantContext.Scope ignored = resolver.openForChat(101L)) {
            assertThat(context.currentTenantId()).isEqualTo(tenantId);
            assertThat(resolver.currentRecipients()).containsExactly(101L);
        }
    }

    @Test
    void rejectsAnUnmappedSecureChat() {
        NotifyRecipientResolver resolver = resolver(new NotifyTenantContext(), UUID.randomUUID(), 101L);

        assertThatThrownBy(() -> resolver.openForChat(202L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsRecipientSetsSeparatedForEachEventTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        NotifyTenantContext context = new NotifyTenantContext();
        NotifyRecipientResolver resolver = new NotifyRecipientResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new NotifyRecipientProperties(List.of(
                        new NotifyRecipientProperties.TenantRecipient(tenantA, List.of(101L, 102L)),
                        new NotifyRecipientProperties.TenantRecipient(tenantB, List.of(201L))
                )),
                context,
                0
        );
        resolver.validateMappings();

        try (NotifyTenantContext.Scope ignored = resolver.openForEvent(tenantA)) {
            assertThat(resolver.currentRecipients()).containsExactly(101L, 102L);
        }
        try (NotifyTenantContext.Scope ignored = resolver.openForEvent(tenantB)) {
            assertThat(resolver.currentRecipients()).containsExactly(201L);
        }
    }

    @Test
    void failsSpringBeanInitializationWhenSecureMappingsAreMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(NotifyRecipientResolver.class, () -> new NotifyRecipientResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new NotifyRecipientProperties(List.of()),
                new NotifyTenantContext(),
                0
        ));

        assertThatThrownBy(context::refresh)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("secure-multi-tenant Notify requires recipient mappings");
        context.close();
    }

    private NotifyRecipientResolver resolver(NotifyTenantContext context, UUID tenantId, long chatId) {
        NotifyRecipientResolver resolver = new NotifyRecipientResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new NotifyRecipientProperties(List.of(new NotifyRecipientProperties.TenantRecipient(tenantId, List.of(chatId)))),
                context,
                0
        );
        resolver.validateMappings();
        return resolver;
    }
}
