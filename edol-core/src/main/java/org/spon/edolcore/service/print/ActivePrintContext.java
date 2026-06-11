package org.spon.edolcore.service.print;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ActivePrintContext {

    private UUID sessionId;

    private String gcodeFile;
    private String subtaskName;

    private Integer totalLayers;

    private Integer savedLayer;
    private Integer savedProgress;

    private Integer remainingTime;

    private Boolean metadataLoaded;

    private String spoolFingerprint;

    private Instant startedAt;
    private Instant lastUpdatedAt;
}