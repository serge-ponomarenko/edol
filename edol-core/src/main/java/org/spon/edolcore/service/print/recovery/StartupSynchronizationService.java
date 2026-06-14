package org.spon.edolcore.service.print.recovery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeContextProvider;
import org.spon.edolcore.service.printer.runtime.RecoveryRuntimeState;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Getter
@Service
@RequiredArgsConstructor
public class StartupSynchronizationService {

    private final PrinterRuntimeContextProvider runtimeContextProvider;

    public void beginRecoverySynchronization(UUID printerId) {
        RecoveryRuntimeState runtime = runtime(printerId);

        runtime.setRecoverySynchronizationActive(true);
        runtime.setSnapshotReadyPublished(false);
    }

    public void completeRecoverySynchronization(UUID printerId) {
        runtime(printerId).setRecoverySynchronizationActive(false);
    }

    public boolean markSnapshotReadyPublished(UUID printerId) {
        RecoveryRuntimeState runtime = runtime(printerId);

        if (runtime.isSnapshotReadyPublished()) {
            return false;
        }

        runtime.setSnapshotReadyPublished(true);
        return true;
    }

    public boolean isRecoverySynchronizationActive(UUID printerId) {
        return runtime(printerId).isRecoverySynchronizationActive();
    }

    public boolean isSnapshotReadyPublished(UUID printerId) {
        return runtime(printerId).isSnapshotReadyPublished();
    }

    private RecoveryRuntimeState runtime(UUID printerId) {
        return runtimeContextProvider
                .getContext(printerId)
                .getRecoveryRuntimeState();
    }

}