package org.spon.edolcore.service.printer.runtime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecoveryRuntimeState {

    private boolean recoverySynchronizationActive;

    private boolean snapshotReadyPublished;

    private boolean recoveryStarted;
}