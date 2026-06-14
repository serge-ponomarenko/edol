package org.spon.edolcore.service.agent;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Getter
@RequiredArgsConstructor
public class AgentStateService {

    private final Map<UUID, AgentState> states =
            new ConcurrentHashMap<>();

    public void update(UUID printerId, AgentHeartbeatDto heartbeat) {
        AgentState state =
                states.computeIfAbsent(
                        printerId,
                        id -> new AgentState()
                );

        state.setWifiConnected(heartbeat.isWifiConnected());
        state.setBambuConnected(heartbeat.isBambuConnected());
        state.setBambuHealthy(heartbeat.isBambuHealthy());
        state.setCameraHealthy(heartbeat.isCameraHealthy());
        state.setEdolConnected(heartbeat.isEdolConnected());
        state.setLastHeartbeat(Instant.now());

    }

    public boolean isOnline(UUID printerId) {
        AgentState state = states.get(printerId);

        if (state == null) {
            return false;
        }

        return state.getLastHeartbeat() != null
                && Duration.between(state.getLastHeartbeat(), Instant.now()).toSeconds() < 60
                && state.isWifiConnected()
                && state.isBambuConnected()
                && state.isBambuHealthy();
    }

    @Data
    private static class AgentState {
        private boolean wifiConnected;
        private boolean bambuConnected;
        private boolean bambuHealthy;
        private boolean cameraHealthy;
        private boolean edolConnected;
        private Instant lastHeartbeat;
    }

}