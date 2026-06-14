package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.spon.edolcore.exception.BambuMqttPublishException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BambuMqttCommandPublisher {

    private final BambuMqttConnectionManager connectionManager;

    public void publish(UUID printerId, String payload) {
        try {
            BambuMqttConnection connection =
                    connectionManager.getConnection(printerId);

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(0);

            if (!connection.isConnected()) {
                throw new BambuMqttPublishException(
                        new IllegalStateException(
                                "Printer " + printerId + " is not connected"
                        )
                );
            }

            connection.getClient().publish(
                    "device/"
                            + connection.getConfiguration()
                            .getPrinter()
                            .getSerialNumber()
                            + "/request",
                    message
            );

        } catch (MqttException e) {
            throw new BambuMqttPublishException(e);
        }
    }
}
