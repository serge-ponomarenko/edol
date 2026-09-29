package org.spon.edolhub.config;

import jakarta.persistence.EntityManagerFactory;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.IdentityContext;
import org.spon.edolhub.service.MissingTenantContextException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;

public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private final transient JdbcTemplate jdbcTemplate;
    private final transient TenantContext tenantContext;
    private final transient IdentityContext identityContext;

    public TenantAwareJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource,
            TenantContext tenantContext,
            IdentityContext identityContext
    ) {
        super(entityManagerFactory);
        setDataSource(dataSource);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.tenantContext = tenantContext;
        this.identityContext = identityContext;
    }

    public TenantAwareJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource,
            TenantContext tenantContext
    ) {
        this(entityManagerFactory, dataSource, tenantContext, new IdentityContext());
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        if (!tenantContext.hasCurrentTenant() && !identityContext.hasCurrentIdentity()) {
            throw new MissingTenantContextException();
        }
        super.doBegin(transaction, definition);
        if (identityContext.hasCurrentIdentity()) {
            IdentityContext.Identity identity = identityContext.getCurrentIdentity();
            jdbcTemplate.queryForObject("select set_config('edol.oidc_issuer', ?, true)", String.class, identity.issuer());
            jdbcTemplate.queryForObject("select set_config('edol.oidc_subject', ?, true)", String.class, identity.subject());
        }
        if (tenantContext.hasCurrentTenant()) {
            jdbcTemplate.queryForObject(
                    "select set_config('edol.tenant_id', ?, true)",
                    String.class,
                    tenantContext.getCurrentTenantId().toString()
            );
        }
    }
}
