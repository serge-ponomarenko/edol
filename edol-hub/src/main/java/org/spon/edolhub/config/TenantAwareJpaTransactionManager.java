package org.spon.edolhub.config;

import jakarta.persistence.EntityManagerFactory;
import org.spon.edolhub.service.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;

public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private final transient JdbcTemplate jdbcTemplate;
    private final transient TenantContext tenantContext;

    public TenantAwareJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource,
            TenantContext tenantContext
    ) {
        super(entityManagerFactory);
        setDataSource(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        jdbcTemplate.queryForObject(
                "select set_config('edol.tenant_id', ?, true)",
                String.class,
                tenantContext.getCurrentTenantId().toString()
        );
    }
}
