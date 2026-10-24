package org.spon.edolcore.service.printer.runtime;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.MetadataAcquisitionService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.scheduling.TaskScheduler;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetadataRetryTenantTest {
    private final CoreTenantContext tenants = new CoreTenantContext();
    private final CoreRuntimeTenantRegistry registry = new CoreRuntimeTenantRegistry(tenants);
    private final CoreRuntimeTenantExecutor executor = new SecureCoreRuntimeTenantExecutor(tenants, registry);
    private final ModelMetadataWorkflowService workflow = mock(ModelMetadataWorkflowService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final PrinterStateService states = mock(PrinterStateService.class);
    private final MetadataAcquisitionService acquisition = new MetadataAcquisitionService(workflow, scheduler,
            new LogContextFactory(), states, executor);

    @Test
    void initialAndScheduledAttemptsRestoreEachTrustedTenantAndClearTheWorker() throws Exception {
        var bindings = Map.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<Runnable> pending = new ArrayList<>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            pending.add(call.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        doAnswer(call -> {
            UUID printer = call.getArgument(0);
            assertThat(tenants.getCurrentTenantId()).isEqualTo(bindings.get(printer));
            throw new IllegalStateException("Synthetic acquisition failure");
        }).when(workflow).requestMetadata(any());
        for (var binding : bindings.entrySet()) {
            try (var ignored = tenants.open(binding.getValue())) { registry.bindCurrentTenant(binding.getKey()); }
            when(states.getState(binding.getKey())).thenReturn(new PrinterState());
            acquisition.start(binding.getKey());
        }
        List<Runnable> retries = List.copyOf(pending);
        try (var worker = Executors.newSingleThreadExecutor()) {
            for (Runnable task : retries) worker.submit(task).get(5, TimeUnit.SECONDS);
            assertThat(worker.submit(tenants::hasCurrentTenant).get(5, TimeUnit.SECONDS)).isFalse();
        }
        for (UUID printer : bindings.keySet()) verify(workflow, times(2)).requestMetadata(printer);
        assertThat(tenants.hasCurrentTenant()).isFalse();
    }

    @Test
    void unknownBindingAndForeignScopeCannotAcquireMetadata() {
        UUID printer = UUID.randomUUID();
        when(states.getState(printer)).thenReturn(new PrinterState());
        assertThatThrownBy(() -> acquisition.start(printer)).isInstanceOf(IllegalStateException.class);
        try (var ignored = tenants.open(UUID.randomUUID())) { registry.bindCurrentTenant(printer); }
        try (var ignored = tenants.open(UUID.randomUUID())) {
            assertThatThrownBy(() -> acquisition.start(printer)).isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(workflow);
    }
}
