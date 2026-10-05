package org.spon.edolcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties("edol-core.legacy-compatibility")
public record CoreLegacyCompatibilityProperties(
        boolean enabled,
        UUID legacyTenantId,
        List<String> allowedCidrs
) {
    public CoreLegacyCompatibilityProperties {
        allowedCidrs = allowedCidrs == null ? List.of() : List.copyOf(allowedCidrs);
    }
}
