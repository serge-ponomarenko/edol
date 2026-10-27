package org.spon.edolhub.service.spool;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.dto.PrintAllocationPreviewDto;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AllocationPreviewRuntimeCacheServiceTest {
    @Test
    void rollbackPreservesPreviouslyCommittedPreview() {
        var cache = new AllocationPreviewRuntimeCacheService();
        UUID printer = UUID.randomUUID();
        var committed = mock(PrintAllocationPreviewDto.class);
        var pending = mock(PrintAllocationPreviewDto.class);
        cache.setCurrentAllocationPreview(printer, committed);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.setCurrentAllocationPreview(printer, pending);
            assertThat(cache.getCurrentAllocationPreview(printer)).isSameAs(pending);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(cache.getCurrentAllocationPreview(printer)).isSameAs(committed);
    }
}
