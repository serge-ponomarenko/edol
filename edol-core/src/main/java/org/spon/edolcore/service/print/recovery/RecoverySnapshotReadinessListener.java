package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.printer.PrinterStateUpdatedEvent;
import org.spon.edolcore.event.recovery.RecoverySnapshotReadyEvent;
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

    @EventListener
    public void onPrinterStateUpdated(PrinterStateUpdatedEvent event) {
        if (!startupSynchronizationService.isRecoverySynchronizationActive(event.getPrinterId())) {
            return;
        }

        UUID printerId = event.getPrinterId();

        log.info(
                "Recovery validator reason: {}",
                recoverySnapshotValidator.explainWhyRecoveryDecisionNotReady(printerId)
        );

        log.info(
                "Recovery snapshot already published={}",
                startupSynchronizationService.isSnapshotReadyPublished(printerId)
        );
        if (!recoverySnapshotValidator.isRecoveryDecisionReady(printerId)) {
            return;
        }

        if (!startupSynchronizationService.markSnapshotReadyPublished(printerId)) {
            return;
        }

        log.info("Recovery snapshot ready");

        events.publishEvent(
                new RecoverySnapshotReadyEvent(printerId)
        );
    }
}