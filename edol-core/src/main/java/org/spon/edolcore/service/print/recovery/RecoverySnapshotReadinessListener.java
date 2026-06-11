package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.printer.PrinterStateUpdatedEvent;
import org.spon.edolcore.event.recovery.RecoverySnapshotReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoverySnapshotReadinessListener {

    private final StartupSynchronizationService startupSynchronizationService;
    private final RecoverySnapshotValidator recoverySnapshotValidator;
    private final ApplicationEventPublisher events;

    @EventListener(PrinterStateUpdatedEvent.class)
    public void onPrinterStateUpdated() {
        if (!startupSynchronizationService.isRecoverySynchronizationActive()) {
            return;
        }

        log.info(
                "Recovery validator reason: {}",
                recoverySnapshotValidator.explainWhyRecoveryDecisionNotReady()
        );

        log.info(
                "Recovery snapshot already published={}",
                startupSynchronizationService.isSnapshotReadyPublished()
        );
        if (!recoverySnapshotValidator.isRecoveryDecisionReady()) {
            return;
        }

        if (!startupSynchronizationService.markSnapshotReadyPublished()) {
            return;
        }

        log.info("Recovery snapshot ready");

        events.publishEvent(
                new RecoverySnapshotReadyEvent()
        );
    }
}