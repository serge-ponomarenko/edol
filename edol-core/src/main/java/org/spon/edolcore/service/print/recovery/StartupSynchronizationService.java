package org.spon.edolcore.service.print.recovery;

import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
public class StartupSynchronizationService {

    private volatile boolean recoverySynchronizationActive = false;
    private volatile boolean snapshotReadyPublished = false;

    public void beginRecoverySynchronization() {
        recoverySynchronizationActive = true;
        snapshotReadyPublished = false;
    }

    public void completeRecoverySynchronization() {
        recoverySynchronizationActive = false;
    }

    public boolean markSnapshotReadyPublished() {
        if (snapshotReadyPublished) {
            return false;
        }

        snapshotReadyPublished = true;
        return true;
    }

}