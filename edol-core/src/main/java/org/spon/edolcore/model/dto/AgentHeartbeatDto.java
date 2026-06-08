package org.spon.edolcore.model.dto;

import lombok.Data;

@Data
public class AgentHeartbeatDto {

    private String agentId;
    private String printerSerial;

    private boolean wifiConnected;
    private boolean bambuConnected;
    private boolean bambuHealthy;
    private boolean cameraHealthy;
    private boolean edolConnected;
}