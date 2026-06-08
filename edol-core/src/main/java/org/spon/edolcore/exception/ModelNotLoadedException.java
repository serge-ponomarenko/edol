package org.spon.edolcore.exception;

public class ModelNotLoadedException extends IllegalStateException {

    public ModelNotLoadedException() {
        super("No model currently loaded");
    }
}
