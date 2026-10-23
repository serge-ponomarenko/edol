package org.spon.edolcore.service.printer.runtime;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.print.recovery.ActivePrintRecoveryService;
import org.spon.edolcore.service.print.recovery.RecoveryStartupCoordinator;
import org.spon.edolcore.service.print.recovery.StartupSynchronizationService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.printer.telemetry.DefaultPrinterTelemetryProvider;
import org.spon.edolcore.service.printer.transport.PrinterTelemetryReconnectScheduler;
import org.spon.edolcore.service.tenant.CoreTenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackgroundRuntimeTenantTest {
    private final CoreTenantContext tenants = new CoreTenantContext();
    private final CoreRuntimeTenantRegistry registry = new CoreRuntimeTenantRegistry(tenants);
    private final CoreRuntimeTenantExecutor scopes = new SecureCoreRuntimeTenantExecutor(tenants, registry);
    private final PrinterRuntimeQueryService runtimes = mock(PrinterRuntimeQueryService.class);
    private final DefaultPrinterTelemetryProvider telemetry = mock(DefaultPrinterTelemetryProvider.class);
    private final ExecutorService submissions = mock(ExecutorService.class);
    private final List<Future<?>> pending = new ArrayList<>();
    private final UUID printerA = UUID.randomUUID();
    private final UUID printerB = UUID.randomUUID();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    @Test
    void scheduledConnectivityCheckAndReconnectBothRunInsideTheirOwnTenant() throws Exception {
        bind(printerA, tenantA);
        bind(printerB, tenantB);
        when(runtimes.getActivePrinterIds()).thenReturn(List.of(printerA, printerB));
        when(telemetry.isConnected(any())).thenAnswer(call -> {
            UUID printer = call.getArgument(0);
            assertThat(tenants.getCurrentTenantId()).isEqualTo(printer.equals(printerA) ? tenantA : tenantB);
            return printer.equals(printerB);
        });
        doAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(tenantA);
            return null;
        }).when(telemetry).connect(printerA);
        try (var worker = Executors.newSingleThreadExecutor()) {
            recordOn(worker);
            scheduler().reconnect();
            awaitTasks();
            assertThat(worker.submit(tenants::hasCurrentTenant).get()).isFalse();
        }
        verify(telemetry).connect(printerA);
        verify(telemetry, never()).connect(printerB);
        assertThat(tenants.hasCurrentTenant()).isFalse();
    }

    @Test
    void startupRecoveryRestoresRuntimeTenantOnTheWorkerAndStartsOnlyOnce() throws Exception {
        bind(printerA, tenantA);
        var runtime = new PrinterRuntime(printerA);
        when(runtimes.getRuntime(printerA)).thenReturn(runtime);
        var synchronization = new StartupSynchronizationService(runtimes);
        var printers = mock(PrinterManagementService.class);
        var commands = mock(PrinterCommandGateway.class);
        when(printers.getPrinter(printerA)).thenAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(tenantA);
            assertThat(synchronization.isRecoverySynchronizationActive(printerA)).isTrue();
            return Printer.builder().id(printerA).build();
        });
        doAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(tenantA);
            return null;
        }).when(commands).pushAll(printerA);
        var recovery = new RecoveryStartupCoordinator(synchronization, mock(ActivePrintRecoveryService.class),
                commands, mock(PrinterStateService.class), printers, runtimes, new LogContextFactory(), submissions, scopes);
        try (var worker = Executors.newSingleThreadExecutor()) {
            recordOn(worker);
            recovery.startRecoveryIfNeeded(printerA);
            recovery.startRecoveryIfNeeded(printerA);
            awaitTasks();
            assertThat(worker.submit(tenants::hasCurrentTenant).get()).isFalse();
        }
        verify(commands).pushAll(printerA);
        assertThat(pending).hasSize(1);
    }

    @Test
    void unknownRuntimeCannotPerformEvenTheConnectivityCheck() throws Exception {
        when(runtimes.getActivePrinterIds()).thenReturn(List.of(printerA));
        try (var worker = Executors.newSingleThreadExecutor()) {
            recordOn(worker);
            scheduler().reconnect();
            assertThatThrownBy(() -> pending.getFirst().get()).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(worker.submit(tenants::hasCurrentTenant).get()).isFalse();
        }
        verifyNoInteractions(telemetry);
    }

    @Test
    void aForeignActiveScopeCannotBeReplacedByARuntimeTenant() {
        bind(printerA, tenantA);
        try (var ignored = tenants.open(tenantB)) {
            assertThatThrownBy(() -> scopes.execute(printerA, () -> telemetry.connect(printerA)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(tenants.getCurrentTenantId()).isEqualTo(tenantB);
        }
        verifyNoInteractions(telemetry);
    }

    private PrinterTelemetryReconnectScheduler scheduler() {
        return new PrinterTelemetryReconnectScheduler(telemetry, runtimes, new LogContextFactory(), submissions, scopes);
    }

    private void bind(UUID printer, UUID tenant) {
        try (var ignored = tenants.open(tenant)) {
            registry.bindCurrentTenant(printer);
        }
    }

    private void recordOn(ExecutorService worker) {
        when(submissions.submit(any(Runnable.class))).thenAnswer(call -> {
            Future<?> future = worker.submit((Runnable) call.getArgument(0));
            pending.add(future);
            return future;
        });
    }

    private void awaitTasks() throws Exception {
        for (Future<?> task : pending) task.get();
    }
}
