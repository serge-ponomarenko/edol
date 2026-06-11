CREATE TABLE active_print_context
(
    session_id        UUID PRIMARY KEY,

    gcode_file        VARCHAR(1024),
    subtask_name      VARCHAR(1024),

    total_layers      INTEGER,

    saved_layer       INTEGER,
    saved_progress    INTEGER,

    remaining_time    INTEGER,

    spool_fingerprint VARCHAR(4000),

    started_at        TIMESTAMP,
    last_updated_at   TIMESTAMP
);