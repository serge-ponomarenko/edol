package org.spon.edolhub.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class TenantAwareTransactionalExecutor {

    private final Executor executor;
    private final TenantContext tenantContext;
    private final PlatformTransactionManager transactionManager;

    public TenantAwareTransactionalExecutor(
            @Qualifier("hubTenantTaskExecutor") Executor executor,
            TenantContext tenantContext,
            PlatformTransactionManager transactionManager
    ) {
        this.executor = executor;
        this.tenantContext = tenantContext;
        this.transactionManager = transactionManager;
    }

    public void execute(Runnable work) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        executor.execute(() -> {
            try (TenantContext.TenantScope ignored = tenantContext.open(tenantId)) {
                executeInCurrentTenantTransaction(work);
            }
        });
    }

    public void executeInCurrentTenantTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
