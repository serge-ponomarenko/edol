package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.spon.edolcore.exception.BambuMqttPublishException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BambuMqttCommandPublisher {

    private final BambuMqttClient bambuMqttClient;

    @Value("${bambu.serial}")
    private String serial;

    public void publish(String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(0);

            bambuMqttClient.getClient().publish(
                    "device/" + serial + "/request",
                    message
            );

        } catch (MqttException e) {
            throw new BambuMqttPublishException(e);
        }
    }
}
