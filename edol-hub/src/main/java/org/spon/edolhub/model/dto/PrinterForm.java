package org.spon.edolhub.model.dto;

import lombok.Data;

@Data
public class PrinterForm {

    private String displayId;
    private String name;
    private String description;
    private String serialNumber;
    private String model;
    private String connectionMode;
    private String cameraProvider;
    private boolean enabled;
    private String mqttHost;
    private Integer mqttPort;
    private String ftpHost;
    private Integer ftpPort;
    private String modelDirectory;
    private String accessCode;
    private String agentId;

    public static PrinterForm from(CorePrinterDto printer, CorePrinterConnectionDto connection) {
        PrinterForm form = new PrinterForm();
        form.setDisplayId(printer.displayId());
        form.setName(printer.name());
        form.setDescription(printer.description());
        form.setSerialNumber(printer.serialNumber());
        form.setModel(printer.model());
        form.setConnectionMode(printer.connectionMode());
        form.setCameraProvider(printer.cameraProvider());
        form.setEnabled(printer.enabled());
        form.setMqttHost(connection.mqttHost());
        form.setMqttPort(connection.mqttPort());
        form.setFtpHost(connection.ftpHost());
        form.setFtpPort(connection.ftpPort());
        form.setModelDirectory(connection.modelDirectory());
        form.setAgentId(connection.agentId());
        return form;
    }
}
