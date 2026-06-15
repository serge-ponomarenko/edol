package org.spon.edolcore.service.model.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class MetadataAcquisitionState {

    // Access guarded by synchronized(state) in MetadataAcquisitionService.
    private ScheduledFuture<?> retryTask;

    private boolean active;

    private int attempt;

}