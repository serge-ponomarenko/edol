package org.spon.edolcore.service.camera;

public interface CameraProvider {

    byte[] capture() throws Exception;

    boolean supports();
}