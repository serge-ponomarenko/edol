package org.spon.edolcore.service.model.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class MetadataAcquisitionState {

    private ScheduledFuture<?> retryTask;

    private boolean active;

    private int attempt;

}