package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.entity.PrintJob;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRuntimeStateServiceTest {

    private final PrintRuntimeStateService service = new PrintRuntimeStateService();

    @Test
    void isolatesRuntimeStateByPrinter() {
        UUID firstPrinter = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondPrinter = UUID.fromString("00000000-0000-0000-0000-000000000102");
        PrintJob firstJob = new PrintJob();
        PrintJob secondJob = new PrintJob();

        service.setCurrentJob(firstPrinter, firstJob);
        service.setAllocationPreviewReady(firstPrinter, true);
        service.setCurrentJob(secondPrinter, secondJob);

        assertThat(service.getCurrentJob(firstPrinter)).isSameAs(firstJob);
        assertThat(service.isAllocationPreviewReady(firstPrinter)).isTrue();
        assertThat(service.getCurrentJob(secondPrinter)).isSameAs(secondJob);
        assertThat(service.isAllocationPreviewReady(secondPrinter)).isFalse();

        service.clear(firstPrinter);

        assertThat(service.getCurrentJob(firstPrinter)).isNull();
        assertThat(service.getCurrentJob(secondPrinter)).isSameAs(secondJob);
    }

    @Test
    void rollbackDiscardsPendingUpdatesAndPreservesOtherPrinters() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PrintJob original = new PrintJob();
        PrintJob other = new PrintJob();
        service.setCurrentJob(first, original);
        service.setCurrentJob(second, other);
        service.setAllocationPreviewReady(first, false);
        begin();
        try {
            service.setCurrentJob(first, new PrintJob());
            service.setAllocationPreviewReady(first, true);
            assertThat(service.getCurrentJob(first)).isNotSameAs(original);
            assertThat(service.isAllocationPreviewReady(first)).isTrue();
        } finally {
            complete(false);
        }
        assertThat(service.getCurrentJob(first)).isSameAs(original);
        assertThat(service.isAllocationPreviewReady(first)).isFalse();
        assertThat(service.getCurrentJob(second)).isSameAs(other);
    }

    @Test
    void commitPublishesUpdatesAndClear() {
        UUID printer = UUID.randomUUID();
        PrintJob job = new PrintJob();
        begin();
        try {
            service.setCurrentJob(printer, job);
            service.setAllocationPreviewReady(printer, true);
        } finally {
            complete(true);
        }
        assertThat(service.getCurrentJob(printer)).isSameAs(job);
        assertThat(service.isAllocationPreviewReady(printer)).isTrue();
        begin();
        try {
            service.clear(printer);
        } finally {
            complete(true);
        }
        assertThat(service.getCurrentJob(printer)).isNull();
        assertThat(service.isAllocationPreviewReady(printer)).isFalse();
    }

    private void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(boolean commit) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        try {
            if (commit) {
                synchronizations.forEach(TransactionSynchronization::afterCommit);
            }
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(commit
                    ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
