package org.spon.edolcore.service.print.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.AmsSlot;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecoverySnapshotValidator {

    private final PrinterStateService printerStateService;

    public boolean isRecoveryDecisionReady(UUID printerId) {
        return explainWhyRecoveryDecisionNotReady(printerId) == null;
    }

    public String explainWhyNotReady(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        if (!isPrintStateRecoverable(state)) {
            return "Printer is not in recoverable state";
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

    public String explainWhyRecoveryDecisionNotReady(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        String gcodeState = state.getGcodeState();

        if (gcodeState == null) {
            return "Missing gcodeState";
        }

        if ("IDLE".equals(gcodeState)
                || "FINISH".equals(gcodeState)
                || "FAILED".equals(gcodeState)) {
            return null;
        }

        return explainWhyNotReady(printerId);
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