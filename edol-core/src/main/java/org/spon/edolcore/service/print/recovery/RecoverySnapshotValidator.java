package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecoverySnapshotValidator {

    private final PrinterStateService printerStateService;

    public boolean isRecoveryDecisionReady() {
        return explainWhyRecoveryDecisionNotReady() == null;
    }

    public String explainWhyNotReady() {
        PrinterState state = printerStateService.getState();

        if (!isPrintStateRecoverable(state)) {
            return "Printer is not in recoverable state";
        }

        if (!hasCurrentFile(state)) {
            return "Missing currentFile";
        }

        if (!hasCurrentTask(state)) {
            return "Missing currentTask";
        }

        if (!hasTotalLayers(state)) {
            return "Missing totalLayers";
        }

        if (!hasSpoolInformation(state)) {
            return "Missing spool information";
        }

        return null;
    }

    public String explainWhyRecoveryDecisionNotReady() {
        PrinterState state = printerStateService.getState();

        String gcodeState = state.getGcodeState();

        if (gcodeState == null) {
            return "Missing gcodeState";
        }

        if ("IDLE".equals(gcodeState)
                || "FINISH".equals(gcodeState)
                || "FAILED".equals(gcodeState)) {
            return null;
        }

        return explainWhyNotReady();
    }

    private boolean isPrintStateRecoverable(PrinterState state) {
        String gcodeState = state.getGcodeState();

        if (gcodeState == null) {
            return false;
        }

        return switch (gcodeState) {
            case "PREPARE", "RUNNING", "PAUSE" -> true;
            default -> false;
        };
    }

    private boolean hasCurrentFile(PrinterState state) {
        return state.getCurrentFile() != null
                && !state.getCurrentFile().isBlank();
    }

    private boolean hasCurrentTask(PrinterState state) {
        return state.getCurrentTask() != null
                && !state.getCurrentTask().isBlank();
    }

    private boolean hasTotalLayers(PrinterState state) {
        return state.getTotalLayers() > 0;
    }

    private boolean hasSpoolInformation(PrinterState state) {
        return hasAmsInformation(state)
                || hasExternalSpoolInformation(state);
    }

    private boolean hasAmsInformation(PrinterState state) {
        if (state.getAms() == null) {
            return false;
        }

        if (state.getAms().getSlots() == null
                || state.getAms().getSlots().isEmpty()) {
            return false;
        }

        return state.getAms().getSlots().stream()
                .map(AmsSlot::getFilamentBrandIndex)
                .anyMatch(idx -> idx != null && !idx.isBlank());
    }

    private boolean hasExternalSpoolInformation(PrinterState state) {
        return state.getExtTray() != null
                && state.getExtTray().getFilamentBrandIndex() != null
                && !state.getExtTray().getFilamentBrandIndex().isBlank();
    }
}