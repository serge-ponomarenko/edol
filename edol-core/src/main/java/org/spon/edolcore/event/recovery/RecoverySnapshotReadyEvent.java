package org.spon.edolcore.event.recovery;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
public class RecoverySnapshotReadyEvent {
    @Getter
    private UUID printerId;
}
