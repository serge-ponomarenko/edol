package org.spon.edolcore.controller.dto.printer;

import lombok.RequiredArgsConstructor;
import com.github.f4b6a3.uuid.UuidCreator;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PrinterMapper {

    /*
     * ==========================
     * Entity -> DTO
     * ==========================
     */

    public PrinterDto toDto(Printer printer) {
        return new PrinterDto(
                printer.getId(),
                printer.getDisplayId(),
                printer.getName(),
                printer.getDescription(),
                printer.getSerialNumber(),
                printer.getModel(),
                printer.getConnectionMode(),
                printer.getCameraProvider(),
                printer.isEnabled()
        );
    }

    public PrinterConnectionDto toDto(PrinterConnectionConfiguration connection) {
        return new PrinterConnectionDto(
                connection.getMqttHost(),
                connection.getMqttPort(),
                connection.getFtpHost(),
                connection.getFtpPort(),
                connection.getModelDirectory(),
                connection.getAccessCode(),
                connection.getAgentId()
        );
    }

    /*
     * ==========================
     * Request -> Entity
     * ==========================
     */

    public Printer createPrinter(CreatePrinterRequest request) {
        return Printer.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .displayId(request.displayId())
                .name(request.name())
                .description(request.description())
                .serialNumber(request.serialNumber())
                .model(request.model())
                .connectionMode(request.connectionMode())
                .cameraProvider(request.cameraProvider())
                .enabled(request.enabled())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public PrinterConnectionConfiguration createConnection(
            Printer printer,
            PrinterConnectionDto dto) {
        return PrinterConnectionConfiguration.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .printer(printer)
                .mqttHost(dto.mqttHost())
                .mqttPort(dto.mqttPort())
                .ftpHost(dto.ftpHost())
                .ftpPort(dto.ftpPort())
                .modelDirectory(dto.modelDirectory())
                .accessCode(dto.accessCode())
                .agentId(dto.agentId())
                .build();
    }

    /*
     * ==========================
     * Update existing entity
     * ==========================
     */

    public void updatePrinter(
            Printer printer,
            UpdatePrinterRequest request) {
        printer.setDisplayId(request.displayId());
        printer.setName(request.name());
        printer.setDescription(request.description());
        printer.setSerialNumber(request.serialNumber());
        printer.setModel(request.model());
        printer.setConnectionMode(request.connectionMode());
        printer.setCameraProvider(request.cameraProvider());
        printer.setEnabled(request.enabled());
        printer.setUpdatedAt(Instant.now());
    }

    public void updateConnection(
            PrinterConnectionConfiguration connection,
            UpdatePrinterConnectionRequest request) {
        connection.setMqttHost(request.mqttHost());
        connection.setMqttPort(request.mqttPort());
        connection.setFtpHost(request.ftpHost());
        connection.setFtpPort(request.ftpPort());
        connection.setModelDirectory(request.modelDirectory());
        connection.setAccessCode(request.accessCode());
        connection.setAgentId(request.agentId());
    }

}
