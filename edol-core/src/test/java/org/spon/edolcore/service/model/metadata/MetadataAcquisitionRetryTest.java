package org.spon.edolcore.service.model.metadata;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetadataAcquisitionRetryTest {
    @Test
    void duplicateFailuresShareOneRetryAndExhaustAfterFourRetries() {
        var workflow = mock(ModelMetadataWorkflowService.class);
        var scheduler = mock(TaskScheduler.class);
        var states = mock(PrinterStateService.class);
        var tenants = mock(CoreRuntimeTenantExecutor.class);
        UUID printer = UUID.randomUUID();
        when(states.getState(printer)).thenReturn(new PrinterState());
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(tenants).execute(eq(printer), any(Runnable.class));
        List<Runnable> pending = new ArrayList<>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            pending.add(call.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        var service = new MetadataAcquisitionService(workflow, scheduler, new LogContextFactory(), states, tenants);
        service.start(printer);
        for (int i = 0; i < 4; i++) {
            service.retryNow(printer);
            service.retryNow(printer);
            org.assertj.core.api.Assertions.assertThat(pending).hasSize(i + 1);
            pending.get(i).run();
        }
        service.retryNow(printer);
        service.retryNow(printer);
        org.assertj.core.api.Assertions.assertThat(pending).hasSize(4);
        verify(workflow, times(5)).requestMetadata(printer);
        service.stop(printer);
        service.retryNow(printer);
        org.assertj.core.api.Assertions.assertThat(pending).hasSize(4);
    }

    @Test
    void successfulMetadataStopCancelsPendingRetryAndIgnoresLateFailure() {
        var workflow = mock(ModelMetadataWorkflowService.class);
        var scheduler = mock(TaskScheduler.class);
        var states = mock(PrinterStateService.class);
        var tenants = mock(CoreRuntimeTenantExecutor.class);
        UUID printer = UUID.randomUUID();
        when(states.getState(printer)).thenReturn(new PrinterState());
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(tenants).execute(eq(printer), any(Runnable.class));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        var service = new MetadataAcquisitionService(workflow, scheduler, new LogContextFactory(), states, tenants);
        service.start(printer);
        service.retryNow(printer);
        service.stop(printer);
        service.retryNow(printer);
        verify(future).cancel(false);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }
}
