package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.printer.PrinterStateUpdatedEvent;
import org.spon.edolcore.event.recovery.RecoverySnapshotReadyEvent;
import org.spon.edolcore.service.LogContextFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoverySnapshotReadinessListener {

    private final StartupSynchronizationService startupSynchronizationService;
    private final RecoverySnapshotValidator recoverySnapshotValidator;
    private final ApplicationEventPublisher events;
    private final LogContextFactory logContextFactory;

    @EventListener
    public void onPrinterStateUpdated(PrinterStateUpdatedEvent event) {
        if (!startupSynchronizationService.isRecoverySynchronizationActive(event.getPrinterId())) {
            return;
        }

        UUID printerId = event.getPrinterId();

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Recovery validator reason: {}",
                        recoverySnapshotValidator.explainWhyRecoveryDecisionNotReady(printerId)
                );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Recovery snapshot already published={}",
                        startupSynchronizationService.isSnapshotReadyPublished(printerId)
                );

        if (!recoverySnapshotValidator.isRecoveryDecisionReady(printerId)) {
            return;
        }

        if (!startupSynchronizationService.markSnapshotReadyPublished(printerId)) {
            return;
        }

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Recovery snapshot ready"
                );

        events.publishEvent(
                new RecoverySnapshotReadyEvent(printerId)
        );
    }
}