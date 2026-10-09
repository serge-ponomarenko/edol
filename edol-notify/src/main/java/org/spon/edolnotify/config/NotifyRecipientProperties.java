package org.spon.edolnotify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties("edol-notify.recipients")
public record NotifyRecipientProperties(List<TenantRecipient> mappings) {

    public NotifyRecipientProperties {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }

    public record TenantRecipient(UUID tenantId, List<Long> chatIds) {
        public TenantRecipient {
            chatIds = chatIds == null ? List.of() : List.copyOf(chatIds);
        }
    }
}
