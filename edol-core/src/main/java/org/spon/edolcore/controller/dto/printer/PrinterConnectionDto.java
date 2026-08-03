package org.spon.edolcore.controller.dto.printer;

public record PrinterConnectionDto(
        String mqttHost,
        Integer mqttPort,
        String ftpHost,
        Integer ftpPort,
        String modelDirectory,
        String accessCode,
        String agentId
) {
}
