package org.spon.edolams.service;

import jakarta.annotation.PostConstruct;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolams.config.AmsPrinterTenantProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AmsPrinterTenantResolver {

    private final DeploymentMode deploymentMode;
    private final AmsPrinterTenantProperties properties;
    private final AmsTenantContext tenantContext;
    private Map<UUID, UUID> tenantsByPrinter = Map.of();

    public AmsPrinterTenantResolver(
            DeploymentMode deploymentMode,
            AmsPrinterTenantProperties properties,
            AmsTenantContext tenantContext
    ) {
        this.deploymentMode = deploymentMode;
        this.properties = properties;
        this.tenantContext = tenantContext;
    }

    @PostConstruct
    void validateMappings() {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return;
        }
        Map<UUID, UUID> mappings = new HashMap<>();
        for (AmsPrinterTenantProperties.PrinterTenant mapping : properties.mappings()) {
            if (mapping.printerId() == null || mapping.tenantId() == null
                    || mappings.putIfAbsent(mapping.printerId(), mapping.tenantId()) != null) {
                throw new IllegalStateException("AMS printer tenant mappings must be complete and unique");
            }
        }
        if (mappings.isEmpty()) {
            throw new IllegalStateException("secure-multi-tenant AMS requires printer tenant mappings");
        }
        tenantsByPrinter = Map.copyOf(mappings);
    }

    public <T> T withPrinter(UUID printerId, Supplier<T> work) {
        AmsTenantContext.Scope scope = openForPrinter(printerId);
        try {
            return work.get();
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    public void withPrinter(UUID printerId, Runnable work) {
        withPrinter(printerId, () -> {
            work.run();
            return null;
        });
    }

    public AmsTenantContext.Scope openForEnvelope(UUID printerId, UUID tenantId) {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return null;
        }
        UUID mappedTenant = mappedTenant(printerId);
        if (!mappedTenant.equals(tenantId)) {
            throw new IllegalArgumentException("AMS MQTT tenant does not own mapped printer " + printerId);
        }
        return tenantContext.open(tenantId);
    }

    private AmsTenantContext.Scope openForPrinter(UUID printerId) {
        if (deploymentMode != DeploymentMode.SECURE_MULTI_TENANT) {
            return null;
        }
        return tenantContext.open(mappedTenant(printerId));
    }

    private UUID mappedTenant(UUID printerId) {
        UUID tenantId = tenantsByPrinter.get(printerId);
        if (tenantId == null) {
            throw new IllegalArgumentException("AMS printer is not mapped to a tenant: " + printerId);
        }
        return tenantId;
    }
}
