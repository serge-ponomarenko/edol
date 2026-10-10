package org.spon.edolams.config;

import jakarta.persistence.EntityManagerFactory;
import org.spon.edolams.service.AmsTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;

public class AmsTenantAwareJpaTransactionManager extends JpaTransactionManager {

    private final transient JdbcTemplate jdbcTemplate;
    private final transient AmsTenantContext tenantContext;

    public AmsTenantAwareJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource,
            AmsTenantContext tenantContext
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
                tenantContext.currentTenantId().toString()
        );
    }
}
