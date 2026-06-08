package org.spon.edolcore.exception;

public class ModelTransferException extends IllegalStateException {

    public ModelTransferException(String fileName, Throwable cause) {
        super("Failed to transfer model: " + fileName, cause);
    }
}
