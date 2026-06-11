package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.MetadataRecoveryService;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.print.ActivePrintContext;
import org.spon.edolcore.service.print.ActivePrintContextService;
import org.spon.edolcore.service.print.SpoolFingerprintBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivePrintRecoveryService {

    private final ActivePrintContextService activePrintContextService;
    private final PrinterStateService printerStateService;
    private final CameraSnapshotStore cameraSnapshotStore;
    private final MetadataRecoveryService metadataRecoveryService;
    private final SpoolFingerprintBuilder spoolFingerprintBuilder;

    public RecoveryResult recover() {
        PrinterState state = printerStateService.getState();
        ActivePrintContext context = activePrintContextService.findAny()
                .orElse(null);

        if (context == null) {
            if (isPrinterPrinting(state)) {
                log.info(
                        "Recovery skipped: no persisted ActivePrintContext, creating new session"
                );
                return RecoveryResult.START_NEW_SESSION;
            }
            log.info(
                    "Recovery skipped: no persisted ActivePrintContext and printer is not printing"
            );
            return RecoveryResult.NO_ACTIVE_PRINT;
        }

        log.info(
                """
                        Recovery comparison
                        
                        Persisted:
                          file={}
                          task={}
                          layers={}
                          layer={}
                          progress={}
                          fingerprint={}
                        
                        Current:
                          file={}
                          task={}
                          layers={}
                          layer={}
                          progress={}
                          fingerprint={}
                        """,
                context.getGcodeFile(),
                context.getSubtaskName(),
                context.getTotalLayers(),
                context.getSavedLayer(),
                context.getSavedProgress(),
                context.getSpoolFingerprint(),

                state.getCurrentFile(),
                state.getCurrentTask(),
                state.getTotalLayers(),
                state.getLayer(),
                state.getProgress(),
                spoolFingerprintBuilder.build(
                        state.getAms(),
                        state.getExtTray()
                )
        );

        if (!isPrinterPrinting(state)) {
            log.warn(
                    "Recovery rejected: printer is not actively printing. Removing stale context {}",
                    context.getSessionId()
            );
            activePrintContextService.delete(context.getSessionId());
            return RecoveryResult.NO_ACTIVE_PRINT;
        }

        if (!matches(context, state)) {
            log.warn(
                    "Recovery rejected: active print does not match persisted context {}",
                    context.getSessionId()
            );
            activePrintContextService.delete(context.getSessionId());
            return RecoveryResult.RECOVERY_REJECTED;
        }

        state.setSessionId(
                context.getSessionId().toString()
        );

        metadataRecoveryService.recoverMetadata(
                context.getGcodeFile()
        );

        cameraSnapshotStore.setCurrentSessionId(
                context.getSessionId().toString()
        );

        log.info(
                "Successfully recovered print session {}",
                context.getSessionId()
        );

        return RecoveryResult.RECOVERED;
    }

    private boolean isPrinterPrinting(PrinterState state) {
        String gcodeState = state.getGcodeState();

        return "PREPARE".equals(gcodeState)
                || "RUNNING".equals(gcodeState)
                || "PAUSE".equals(gcodeState);
    }

    private boolean matches(
            ActivePrintContext context,
            PrinterState state
    ) {
        if (safeNotEquals(
                context.getGcodeFile(),
                state.getCurrentFile()
        )) {
            log.warn(
                    "Recovery mismatch: gcodeFile persisted='{}', current='{}'",
                    context.getGcodeFile(),
                    state.getCurrentFile()
            );
            return false;
        }

        if (safeNotEquals(
                context.getSubtaskName(),
                state.getCurrentTask()
        )) {
            log.warn(
                    "Recovery mismatch: subtask persisted='{}', current='{}'",
                    context.getSubtaskName(),
                    state.getCurrentTask()
            );
            return false;
        }

        if (safeNotEquals(
                context.getTotalLayers(),
                state.getTotalLayers()
        )) {
            log.warn(
                    "Recovery mismatch: totalLayers persisted={}, current={}",
                    context.getTotalLayers(),
                    state.getTotalLayers()
            );
            return false;
        }

        if (state.getLayer() < context.getSavedLayer()) {
            log.warn(
                    "Recovery mismatch: current layer {} < persisted layer {}",
                    state.getLayer(),
                    context.getSavedLayer()
            );
            return false;
        }

        if (state.getProgress() < context.getSavedProgress()) {
            log.warn(
                    "Recovery mismatch: current progress {} < persisted progress {}",
                    state.getProgress(),
                    context.getSavedProgress()
            );
            return false;
        }

        String currentFingerprint =
                spoolFingerprintBuilder.build(
                        state.getAms(),
                        state.getExtTray()
                );

        if (safeNotEquals(
                context.getSpoolFingerprint(),
                currentFingerprint
        )) {
            log.warn(
                    """
                            Recovery mismatch: spool fingerprint
                            
                            persisted={}
                            current={}
                            """,
                    context.getSpoolFingerprint(),
                    currentFingerprint
            );

            return false;
        }

        return true;
    }

    private boolean safeNotEquals(Object left, Object right) {
        if (left == null) {
            return right != null;
        }
        return !left.equals(right);
    }
}