package org.spon.edolcore.service.camera;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public interface CameraProvider {

    /** Returns no frame when capture is unavailable or completes asynchronously. */
    byte @Nullable [] capture(UUID printerId) throws
            NoSuchAlgorithmException,
            KeyManagementException,
            IOException;

    boolean supports(UUID printerId);
}
