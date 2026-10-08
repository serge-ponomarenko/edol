package org.spon.edolcore.service.mqtt;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureCoreEventTenantResolver implements CoreEventTenantResolver {

    private final JdbcClient catalogJdbc;

    SecureCoreEventTenantResolver(@Qualifier("coreCatalogDataSource") DataSource catalogDataSource) {
        this.catalogJdbc = JdbcClient.create(catalogDataSource);
    }

    @Override
    public Optional<UUID> tenantIdForPrinter(UUID printerId) {
        return catalogJdbc.sql("select tenant_id from core.printers where id = :printerId")
                .param("printerId", printerId)
                .query(UUID.class)
                .optional();
    }
}
