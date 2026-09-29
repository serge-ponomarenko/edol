package org.spon.edolhub.config;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class HubTenantIdentifierConfiguration {

    private final HubTenantIdentifierResolver tenantIdentifierResolver;

    @Bean
    HibernatePropertiesCustomizer hubTenantIdentifierResolverCustomizer() {
        return properties -> properties.put(
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                tenantIdentifierResolver
        );
    }
}
