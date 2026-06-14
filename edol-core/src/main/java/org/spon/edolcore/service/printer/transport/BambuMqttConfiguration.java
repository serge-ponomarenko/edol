package org.spon.edolcore.service.printer.transport;

public record BambuMqttConfiguration(
        String clientId,
        Integer connectionTimeout
) {
}