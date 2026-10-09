package org.spon.edolnotify.service;

import jakarta.annotation.PostConstruct;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolnotify.config.NotifyRecipientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotifyRecipientResolver {

    private final DeploymentMode deploymentMode;
    private final NotifyRecipientProperties properties;
    private final NotifyTenantContext tenantContext;
    private final long homeAdminChatId;
    private Map<UUID, List<Long>> recipientsByTenant = Map.of();
    private Map<Long, UUID> tenantsByRecipient = Map.of();

    public NotifyRecipientResolver(
            DeploymentMode deploymentMode,
            NotifyRecipientProperties properties,
            NotifyTenantContext tenantContext,
            @Value("${telegram.admin-id:0}") long homeAdminChatId
    ) {
        this.deploymentMode = deploymentMode;
        this.properties = properties;
        this.tenantContext = tenantContext;
        this.homeAdminChatId = homeAdminChatId;
    }

    @PostConstruct
    void validateMappings() {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return;
        }
        Map<UUID, List<Long>> byTenant = new HashMap<>();
        Map<Long, UUID> byRecipient = new HashMap<>();
        for (NotifyRecipientProperties.TenantRecipient mapping : properties.mappings()) {
            if (mapping.tenantId() == null || mapping.chatIds().isEmpty()) {
                throw new IllegalStateException("Notify recipient mappings require a tenant and at least one chat");
            }
            if (byTenant.putIfAbsent(mapping.tenantId(), mapping.chatIds()) != null) {
                throw new IllegalStateException("Notify recipient mappings must not repeat a tenant");
            }
            for (Long chatId : mapping.chatIds()) {
                if (chatId == null || byRecipient.putIfAbsent(chatId, mapping.tenantId()) != null) {
                    throw new IllegalStateException("Notify recipient mappings must not share a chat between tenants");
                }
            }
        }
        if (byTenant.isEmpty()) {
            throw new IllegalStateException("secure-multi-tenant Notify requires recipient mappings");
        }
        recipientsByTenant = Map.copyOf(byTenant);
        tenantsByRecipient = Map.copyOf(byRecipient);
    }

    public NotifyTenantContext.Scope openForEvent(UUID tenantId) {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return null;
        }
        if (!recipientsByTenant.containsKey(tenantId)) {
            throw new IllegalArgumentException("No Notify recipients are configured for tenant " + tenantId);
        }
        return tenantContext.open(tenantId);
    }

    public NotifyTenantContext.Scope openForChat(long chatId) {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            if (chatId != homeAdminChatId) {
                throw new IllegalArgumentException("Telegram chat is not authorized");
            }
            return null;
        }
        UUID tenantId = tenantsByRecipient.get(chatId);
        if (tenantId == null) {
            throw new IllegalArgumentException("Telegram chat is not configured for an EDOL tenant");
        }
        return tenantContext.open(tenantId);
    }

    public List<Long> currentRecipients() {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return List.of(homeAdminChatId);
        }
        return recipientsByTenant.get(tenantContext.currentTenantId());
    }
}
