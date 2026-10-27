package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalRuntimeMapTest {
    @Test
    void uncommittedValueIsPrivateAndSuspendedTransactionIsRestored() {
        TransactionalRuntimeMap<String> values = new TransactionalRuntimeMap<>();
        UUID printer = UUID.randomUUID();
        values.put(printer, "old");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            values.put(printer, "pending");
            assertThat(values.get(printer)).isEqualTo("pending");
            assertThat(CompletableFuture.supplyAsync(() -> values.get(printer)).join()).isEqualTo("old");
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::suspend);
            assertThat(values.get(printer)).isEqualTo("old");
            synchronizations.forEach(TransactionSynchronization::resume);
            assertThat(values.get(printer)).isEqualTo("pending");
            synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(values.get(printer)).isEqualTo("old");
    }
}
