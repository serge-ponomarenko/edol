package org.spon.edolcore.service.printer.transport;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.spon.edolcore.util.SslUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BambuMqttClient implements MqttCallback {

    private final BambuTelemetryConsumer telemetryConsumer;
    private final PrinterConnectivityStateService connectivityStateService;

    @Getter
    private MqttClient client;

    @Value("${bambu.host}")
    private String host;

    @Value("${bambu.serial}")
    private String serial;

    @Value("${bambu.access-code}")
    private String accessCode;

    @Value("${bambu.client-id}")
    private String clientId;

    @Value("${bambu.connection-timeout}")
    private Integer bambuConnectionTimeout;

    public synchronized void connect() {
        try {

            if (client != null && client.isConnected()) {
                return;
            }

            client = new MqttClient(host, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("bblp");
            options.setPassword(accessCode.toCharArray());
            options.setAutomaticReconnect(false);
            options.setSocketFactory(SslUtil.createTrustAllSocketFactory());
            options.setKeepAliveInterval(60);

            // Disable hostname verification
            options.setHttpsHostnameVerificationEnabled(false);
            options.setConnectionTimeout(bambuConnectionTimeout);  // seconds

            client.setCallback(this);

            client.connect(options);

            client.subscribe("device/" + serial + "/report");

            log.info("Connected to Bambu MQTT");

            connectivityStateService.setConnected();

        } catch (Exception e) {
            log.warn("Printer connection failed: {}", e.getMessage());
        }

    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT connection lost");
        connectivityStateService.setDisconnected();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        telemetryConsumer.consume(
                message.getPayload()
        );
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Nothing
    }

}