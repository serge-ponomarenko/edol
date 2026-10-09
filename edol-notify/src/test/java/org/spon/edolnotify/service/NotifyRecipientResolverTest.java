package org.spon.edolnotify.service;

import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolnotify.config.NotifyRecipientProperties;

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
