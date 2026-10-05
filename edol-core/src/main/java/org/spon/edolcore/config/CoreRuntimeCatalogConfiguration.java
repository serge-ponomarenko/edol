package org.spon.edolcore.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
@EnableConfigurationProperties(CoreLegacyCompatibilityProperties.class)
class CoreRuntimeCatalogConfiguration {

    @Bean
    @ConfigurationProperties("edol-core.catalog-datasource")
    DataSourceProperties coreCatalogDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "coreCatalogDataSource")
    @ConfigurationProperties("edol-core.catalog-datasource.hikari")
    HikariDataSource coreCatalogDataSource(
            @Qualifier("coreCatalogDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
