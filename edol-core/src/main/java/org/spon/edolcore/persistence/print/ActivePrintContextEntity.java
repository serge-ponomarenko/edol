package org.spon.edolcore.persistence.print;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_print_context")
@Getter
@Setter
public class ActivePrintContextEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "gcode_file")
    private String gcodeFile;

    @Column(name = "subtask_name")
    private String subtaskName;

    @Column(name = "total_layers")
    private Integer totalLayers;

    @Column(name = "saved_layer")
    private Integer savedLayer;

    @Column(name = "saved_progress")
    private Integer savedProgress;

    @Column(name = "remaining_time")
    private Integer remainingTime;

    @Column(name = "spool_fingerprint", length = 4000)
    private String spoolFingerprint;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;
}