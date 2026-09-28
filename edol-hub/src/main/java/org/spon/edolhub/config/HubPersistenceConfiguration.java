package org.spon.edolhub.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.MultiTenancySettings;
import org.spon.edolhub.service.TenantContext;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class HubPersistenceConfiguration {

    private final HubTenantIdentifierResolver tenantIdentifierResolver;
    private final TenantContext tenantContext;

    @Bean
    HibernatePropertiesCustomizer hubTenantIdentifierResolverCustomizer() {
        return properties -> properties.put(
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                tenantIdentifierResolver
        );
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory, dataSource, tenantContext);
    }
}
