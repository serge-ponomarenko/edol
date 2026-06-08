package org.spon.edolcore.exception;

public class TimelapseGenerationException extends IllegalStateException {

    public TimelapseGenerationException(String jobId, Throwable cause) {
        super("Failed to generate timelapse for job: " + jobId, cause);
    }

    public TimelapseGenerationException(String jobId) {
        super("FFmpeg failed for job: " + jobId);
    }
}
