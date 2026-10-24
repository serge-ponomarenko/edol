package org.spon.edolcore.service.camera;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.agent.command.AgentCommandGateway;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class AgentCameraProvider
        implements CameraProvider {

    private final AgentCommandGateway commands;

    @Override
    public byte[] capture(UUID printerId) {
        commands.uploadSnapshot(printerId);
        // Asynchronous HTTPS upload stores the frame; the background capture must not overwrite it.
        return null;
    }

    @Override
    public boolean supports(UUID printerId) {
        return true;
    }
}
