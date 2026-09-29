package org.spon.edolhub.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class HubPersistenceConfiguration {

    private final TenantContext tenantContext;

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory, dataSource, tenantContext);
    }
}
