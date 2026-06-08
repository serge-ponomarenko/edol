package org.spon.edolcore.exception;

public class BambuMqttPublishException extends IllegalStateException {

    public BambuMqttPublishException(Throwable cause) {
        super("Cannot publish printer command", cause);
    }
}
