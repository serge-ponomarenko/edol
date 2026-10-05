package org.spon.edolcore.service.printer.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
class SecureCoreRuntimeCatalogEnumerator implements CoreRuntimeCatalogEnumerator {

    private final JdbcClient catalogJdbc;

    SecureCoreRuntimeCatalogEnumerator(@Qualifier("coreCatalogDataSource") DataSource catalogDataSource) {
        this.catalogJdbc = JdbcClient.create(catalogDataSource);
    }

    @Override
    public List<CoreRuntimeCatalogEntry> enabledPrinters() {
        return catalogJdbc.sql("""
                        select id, tenant_id
                        from core.printers
                        where enabled
                        order by id
                        """)
                .query((resultSet, rowNum) -> new CoreRuntimeCatalogEntry(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getObject("tenant_id", java.util.UUID.class)
                ))
                .list();
    }
}
