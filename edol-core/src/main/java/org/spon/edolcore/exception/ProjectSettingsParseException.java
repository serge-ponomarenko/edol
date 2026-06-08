package org.spon.edolcore.exception;

public class ProjectSettingsParseException extends IllegalStateException {

    public ProjectSettingsParseException(Throwable cause) {
        super("Failed to parse project_settings.config", cause);
    }
}
