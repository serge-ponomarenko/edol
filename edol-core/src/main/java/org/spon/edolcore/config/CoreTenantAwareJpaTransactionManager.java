package org.spon.edolcore.config;

import jakarta.persistence.EntityManagerFactory;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.tenant.MissingCoreTenantContextException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;

class CoreTenantAwareJpaTransactionManager extends JpaTransactionManager {

    private final transient JdbcTemplate jdbcTemplate;
    private final transient CoreTenantContext tenantContext;

    CoreTenantAwareJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource,
            CoreTenantContext tenantContext
    ) {
        super(entityManagerFactory);
        setDataSource(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        if (!tenantContext.hasCurrentTenant()) {
            throw new MissingCoreTenantContextException();
        }
        super.doBegin(transaction, definition);
        jdbcTemplate.queryForObject(
                "select set_config('edol.tenant_id', ?, true)",
                String.class,
                tenantContext.getCurrentTenantId().toString()
        );
    }
}
