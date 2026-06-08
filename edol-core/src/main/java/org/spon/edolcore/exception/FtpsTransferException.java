package org.spon.edolcore.exception;

public class FtpsTransferException extends IllegalStateException {

    public FtpsTransferException(String message) {
        super(message);
    }

    public FtpsTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
