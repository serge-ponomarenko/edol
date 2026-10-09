package org.spon.edolams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties("edol-ams.printer-tenants")
public record AmsPrinterTenantProperties(List<PrinterTenant> mappings) {

    public AmsPrinterTenantProperties {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }

    public record PrinterTenant(UUID printerId, UUID tenantId) {
    }
}
