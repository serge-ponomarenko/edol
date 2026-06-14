package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.event.recovery.RecoverySnapshotReadyEvent;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.PrinterService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryStartupCoordinator {

    private final StartupSynchronizationService startupSynchronizationService;
    private final ActivePrintRecoveryService activePrintRecoveryService;
    private final PrinterCommandGateway printerCommandGateway;
    private final PrinterStateService printerStateService;
    private final PrinterService printerService;

    private final AtomicBoolean recoveryStarted = new AtomicBoolean(false);

    public void startRecoveryIfNeeded(UUID printerId) {
        if (!recoveryStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("Starting recovery after printer connectivity established");
        Thread.ofVirtual().start(() -> runRecoveryWorkflow(printerId));
    }

    private void runRecoveryWorkflow(UUID printerId) {
        startupSynchronizationService.beginRecoverySynchronization(printerId);

        try {
            log.info("Starting recovery synchronization");
            printerCommandGateway.pushAll(
                    printerService.getPrinter(printerId).getId()
            );
        } catch (Exception e) {
            log.error("Recovery startup failed", e);
            startupSynchronizationService.completeRecoverySynchronization(printerId);
        }
    }

    @EventListener
    public void onRecoverySnapshotReady(RecoverySnapshotReadyEvent event) {
        UUID printerId = event.getPrinterId();

        try {
            RecoveryResult result = activePrintRecoveryService.recover(printerId);

            log.info("Recovery finished with result {}", result);

            switch (result) {
                case START_NEW_SESSION, RECOVERY_REJECTED -> {
                    log.info(
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
            log.error("Recovery workflow failed", e);
        } finally {
            startupSynchronizationService.completeRecoverySynchronization(printerId);
            log.info("Recovery synchronization completed");
        }
    }

}