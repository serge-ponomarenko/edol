package org.spon.edolcore.exception;

public class PlateParseException extends IllegalStateException {

    public PlateParseException(Throwable cause) {
        super("Failed to parse plate file", cause);
    }
}
