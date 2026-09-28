package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAwareTransactionalExecutorTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");

    @Test
    void establishesAndCleansTenantContextInTheWorkerThread() throws Exception {
        TenantContext tenantContext = new TenantContext();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            TenantAwareTransactionalExecutor executor = new TenantAwareTransactionalExecutor(
                    worker,
                    tenantContext,
                    new NoOpTransactionManager()
            );
            Future<UUID> observedTenant = worker.submit(() -> {
                try (TenantContext.TenantScope ignored = tenantContext.open(TENANT_ID)) {
                    executor.execute(() -> assertThat(tenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID));
                }
                return null;
            });
            observedTenant.get();

            Future<Boolean> contextWasCleaned = worker.submit(() -> {
                assertThatThrownBy(tenantContext::getCurrentTenantId)
                        .isInstanceOf(MissingTenantContextException.class);
                return true;
            });
            assertThat(contextWasCleaned.get()).isTrue();
        } finally {
            worker.shutdownNow();
        }
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // This test double deliberately has no physical transaction resource.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // This test double deliberately has no physical transaction resource.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // This test double deliberately has no physical transaction resource.
        }
    }
}
