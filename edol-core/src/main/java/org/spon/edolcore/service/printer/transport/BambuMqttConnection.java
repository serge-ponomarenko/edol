package org.spon.edolcore.service.printer.transport;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.spon.edolcore.util.SslUtil;

import java.util.UUID;

@Slf4j
public class BambuMqttConnection implements MqttCallback {

    private static final String PRINTER_ID_KEY = "printerId";

    private final UUID printerId;
    @Getter
    private final PrinterConnectionConfiguration configuration;
    private final BambuMqttConfiguration mqttConfiguration;
    private final BambuTelemetryConsumer telemetryConsumer;
    private final PrinterConnectivityStateService connectivityStateService;

    @Getter
    private MqttClient client;

    public BambuMqttConnection(
            UUID printerId,
            PrinterConnectionConfiguration configuration,
            BambuMqttConfiguration bambuMqttConfiguration,
            BambuTelemetryConsumer telemetryConsumer,
            PrinterConnectivityStateService connectivityStateService
    ) {
        this.printerId = printerId;
        this.configuration = configuration;
        this.mqttConfiguration = bambuMqttConfiguration;
        this.telemetryConsumer = telemetryConsumer;
        this.connectivityStateService = connectivityStateService;
    }

    public synchronized void connect() {
        try {
            if (client != null && client.isConnected()) {
                return;
            }

            String connectUrl =
                    "ssl://" +
                            configuration.getMqttHost() + ":" + configuration.getMqttPort();

            client = new MqttClient(
                    connectUrl,
                    mqttConfiguration.clientId()
            );

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("bblp");
            options.setPassword(configuration.getAccessCode().toCharArray());
            options.setAutomaticReconnect(false);
            options.setSocketFactory(SslUtil.createTrustAllSocketFactory());
            options.setKeepAliveInterval(60);

            // Disable hostname verification
            options.setHttpsHostnameVerificationEnabled(false);
            options.setConnectionTimeout(
                    mqttConfiguration.connectionTimeout()
            );  // seconds

            client.setCallback(this);

            client.connect(options);

            client.subscribe("device/" + configuration.getPrinter().getSerialNumber() + "/report");

            log.atInfo()
                    .addKeyValue(PRINTER_ID_KEY, printerId)
                    .log("Connected to Bambu MQTT");

            connectivityStateService.setConnected(printerId);

        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue(PRINTER_ID_KEY, printerId)
                    .log("Printer connection failed: {}", e.getMessage());
        }

    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void messageArrived(
            String topic,
            MqttMessage message
    ) {
        telemetryConsumer.consume(
                printerId,
                message.getPayload()
        );
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Nothing
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.atError()
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .log("MQTT connection lost");

        connectivityStateService.setDisconnected(
                printerId
        );
    }

    public void disconnect() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }

                client.close();
                client = null;
            }
        } catch (MqttException e) {
            log.atWarn()
                    .addKeyValue(PRINTER_ID_KEY, printerId)
                    .log("Failed to disconnect MQTT client");
        }
    }
}
