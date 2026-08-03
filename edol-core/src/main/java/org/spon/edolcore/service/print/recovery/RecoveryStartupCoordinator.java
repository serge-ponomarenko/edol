package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.event.recovery.RecoverySnapshotReadyEvent;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.PrinterManagementService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeContextProvider;
import org.spon.edolcore.service.printer.runtime.RecoveryRuntimeState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryStartupCoordinator {

    private final StartupSynchronizationService startupSynchronizationService;
    private final ActivePrintRecoveryService activePrintRecoveryService;
    private final PrinterCommandGateway printerCommandGateway;
    private final PrinterStateService printerStateService;
    private final PrinterManagementService printerManagementService;
    private final PrinterRuntimeContextProvider printerRuntimeContextProvider;
    private final LogContextFactory logContextFactory;
    private final ExecutorService virtualThreadExecutor;

    public void startRecoveryIfNeeded(UUID printerId) {
        RecoveryRuntimeState runtimeState = printerRuntimeContextProvider
                .getContext(printerId)
                .getRecoveryRuntimeState();

        synchronized (runtimeState) {
            if (runtimeState.isRecoveryStarted()) {
                return;
            }
            runtimeState.setRecoveryStarted(true);
        }

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Starting recovery after printer connectivity established"
                );

        virtualThreadExecutor.submit(() -> runRecoveryWorkflow(printerId));
    }

    private void runRecoveryWorkflow(UUID printerId) {
        startupSynchronizationService.beginRecoverySynchronization(printerId);

        try {
            logContextFactory
                    .printer(
                            log.atInfo(),
                            printerId
                    )
                    .log(
                            "Starting recovery synchronization"
                    );
            printerCommandGateway.pushAll(
                    printerManagementService.getPrinter(printerId).getId()
            );
        } catch (Exception e) {
            logContextFactory
                    .printer(
                            log.atError(),
                            printerId
                    )
                    .log(
                            "Recovery startup failed", e
                    );
            startupSynchronizationService.completeRecoverySynchronization(printerId);
        }
    }

    @EventListener
    public void onRecoverySnapshotReady(RecoverySnapshotReadyEvent event) {
        UUID printerId = event.getPrinterId();

        try {
            RecoveryResult result = activePrintRecoveryService.recover(printerId);

            logContextFactory
                    .printer(
                            log.atInfo(),
                            printerId
                    )
                    .log(
                            "Recovery finished with result {}", result
                    );

            switch (result) {
                case START_NEW_SESSION, RECOVERY_REJECTED -> {
                    logContextFactory
                            .printer(
                                    log.atInfo(),
                                    printerId
                            )
                            .log(
                                    "Recovery did not restore an existing session. Starting a new print session"
                            );

                    printerStateService.publish(
                            printerId,
                            PrinterEventType.PRINT_STARTED
                    );
                }

                case RECOVERED, NO_ACTIVE_PRINT -> {
                    // no-op
                }
            }

        } catch (Exception e) {
            logContextFactory
                    .printer(
                            log.atError(),
                            printerId
                    )
                    .log(
                            "Recovery workflow failed", e
                    );
        } finally {
            startupSynchronizationService.completeRecoverySynchronization(printerId);
            logContextFactory
                    .printer(
                            log.atInfo(),
                            printerId
                    )
                    .log(
                            "Recovery synchronization completed"
                    );
        }
    }

}