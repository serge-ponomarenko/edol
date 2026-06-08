package org.spon.edolcore.service.agent;

import lombok.Getter;
import org.spon.edolcore.model.dto.AgentHeartbeatDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Getter
public class AgentStateService {

    private volatile String agentId;
    private volatile String printerSerial;

    private volatile boolean wifiConnected;
    private volatile boolean bambuConnected;
    private volatile boolean bambuHealthy;
    private volatile boolean cameraHealthy;
    private volatile boolean edolConnected;

    private volatile Instant lastHeartbeat;

    public void update(AgentHeartbeatDto heartbeat) {
        this.agentId = heartbeat.getAgentId();
        this.printerSerial = heartbeat.getPrinterSerial();

        this.wifiConnected = heartbeat.isWifiConnected();
        this.bambuConnected = heartbeat.isBambuConnected();
        this.bambuHealthy = heartbeat.isBambuHealthy();
        this.cameraHealthy = heartbeat.isCameraHealthy();
        this.edolConnected = heartbeat.isEdolConnected();

        this.lastHeartbeat = Instant.now();
    }

    public boolean isOnline() {
        return lastHeartbeat != null
                && Duration.between(lastHeartbeat, Instant.now()).toSeconds() < 60
                && wifiConnected
                && bambuConnected
                && bambuHealthy;
    }
}