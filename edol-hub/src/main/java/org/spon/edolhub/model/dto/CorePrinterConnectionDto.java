package org.spon.edolhub.model.dto;

public record CorePrinterConnectionDto(
        String mqttHost,
        Integer mqttPort,
        String ftpHost,
        Integer ftpPort,
        String modelDirectory,
        String accessCode,
        String agentId
) {
}
