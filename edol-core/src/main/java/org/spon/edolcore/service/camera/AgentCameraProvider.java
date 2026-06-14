package org.spon.edolcore.service.camera;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class AgentCameraProvider
        implements CameraProvider {

    @Override
    public byte[] capture(UUID printerId) {
        throw new UnsupportedOperationException(
                "Agent camera snapshots are not implemented"
        );
    }

    @Override
    public boolean supports(UUID printerId) {
        return false;
    }
}
