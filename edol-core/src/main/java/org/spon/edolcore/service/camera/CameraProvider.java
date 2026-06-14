package org.spon.edolcore.service.camera;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public interface CameraProvider {

    byte[] capture(UUID printerId) throws
            NoSuchAlgorithmException,
            KeyManagementException,
            IOException;

    boolean supports(UUID printerId);
}