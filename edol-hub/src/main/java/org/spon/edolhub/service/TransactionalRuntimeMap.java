package org.spon.edolhub.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Publishes runtime values only after their enclosing transaction commits. */
public final class TransactionalRuntimeMap<T> {
    private final Map<UUID, T> committed = new ConcurrentHashMap<>();
    private final Object resourceKey = new Object();

    public T get(UUID printerId) {
        Pending<T> pending = pending();
        if (pending != null && pending.values.containsKey(printerId)) {
            return pending.values.get(printerId);
        }
        return committed.get(printerId);
    }

    public void put(UUID printerId, T value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(printerId, value);
            return;
        }
        Pending<T> pending = pending();
        if (pending == null) {
            pending = new Pending<>();
            Pending<T> updates = pending;
            TransactionSynchronizationManager.bindResource(resourceKey, updates);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void suspend() {
                    TransactionSynchronizationManager.unbindResource(resourceKey);
                }

                @Override
                public void resume() {
                    TransactionSynchronizationManager.bindResource(resourceKey, updates);
                }

                @Override
                public void afterCommit() {
                    updates.values.forEach(TransactionalRuntimeMap.this::publish);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
                }
            });
        }
        pending.values.put(printerId, value);
    }

    @SuppressWarnings("unchecked")
    private Pending<T> pending() {
        return (Pending<T>) TransactionSynchronizationManager.getResource(resourceKey);
    }

    private void publish(UUID printerId, T value) {
        if (value == null) {
            committed.remove(printerId);
        } else {
            committed.put(printerId, value);
        }
    }

    private static final class Pending<T> {
        private final Map<UUID, T> values = new HashMap<>();
    }
}
